/*
 * Copyright 2026 os890
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.os890.jawelte.module.jta.impl;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.persistence.PersistenceUnitTransactionType;
import jakarta.persistence.RollbackException;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.UserTransaction;

import org.os890.jawelte.core.api.port.ServicePriorityResolver;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.event.TransactionBeforeCompletion;
import org.os890.jawelte.module.jpa.api.event.TransactionCommitted;
import org.os890.jawelte.module.jpa.api.event.TransactionRolledBack;
import org.os890.jawelte.module.jpa.api.event.TransactionStarted;
import org.os890.jawelte.module.jpa.api.port.TransactionStrategy;
import org.os890.jawelte.module.jta.api.port.TransactionManagerProvider;

/**
 * JTA-backed {@link TransactionStrategy} shipped by jta-module. Wins
 * over jpa-module's
 * {@code DefaultResourceLocalTransactionStrategy}
 * ({@code @Priority(Integer.MAX_VALUE)}) by carrying
 * {@code @Priority(Integer.MAX_VALUE - 100)}, so the project-wide
 * {@code ServicePriorityResolver} picks it whenever {@code jta-module}
 * is on the classpath.
 *
 * <p>The {@link TransactionManager} is JVM-singleton: lazily resolved on
 * first {@link #begin()} via
 * {@code TestContext.loadService(TransactionManagerProvider.class)},
 * cached for the JVM lifetime, and released by {@link #shutdown()}. If
 * the priority-leading provider's {@link TransactionManagerProvider#isAvailable()}
 * returns {@code false}, the strategy walks the priority-sorted
 * candidate list (via the active {@link ServicePriorityResolver}) until
 * an available one is found; if none is available, bootstrap fails fast
 * with {@link IllegalStateException}.
 *
 * <p>Nested {@code @Transactional} invocations use real JTA
 * {@link TransactionManager#suspend() suspend} /
 * {@link TransactionManager#resume(Transaction) resume}: the outer
 * {@code Transaction} is suspended, pushed onto a per-thread
 * {@link Deque}, then a fresh inner one is begun. On inner
 * commit / rollback the saved outer is resumed.
 *
 * <p>{@code EntityManager} ↔ JTA-transaction enlistment is the JPA
 * provider's responsibility — Hibernate sees
 * {@code hibernate.transaction.jta.platform=…StandaloneJtaPlatform}
 * (contributed by the active {@code PersistencePropertyResolver}) and
 * enlists each managed {@code EntityManager} on first use. The strategy
 * never references {@code TransactionScopedEmHolder},
 * {@code EmStackFrame}, or any other class from {@code jpa-module/impl}.
 *
 * <p>{@code @TransactionScoped} bean storage stays inside jpa-module's
 * {@code TransactionScopedContext} via the
 * {@code TransactionalInterceptor.activate()} / {@code .deactivate()}
 * brackets — the strategy plays no part in the bean-store lifecycle.
 *
 * <p>CDI events fire <strong>once per JTA transaction</strong> (not once
 * per PU as under RESOURCE_LOCAL): a multi-PU {@code @Transactional}
 * method produces exactly one {@link TransactionStarted} and one
 * {@link TransactionCommitted} or {@link TransactionRolledBack}. The
 * {@code persistenceUnitName} field is empty on these events to signal
 * "transaction-wide" rather than "PU-scoped".
 */
@Priority(Integer.MAX_VALUE - 100)
public class JtaTransactionStrategy implements TransactionStrategy {

    private static final Logger LOG = System.getLogger(JtaTransactionStrategy.class.getName());

    /**
     * Per-thread stack of suspended outer JTA transactions. Populated
     * on nested {@link #begin()} calls (when a transaction is already
     * active on the thread) and drained on the matching commit /
     * rollback. Static so any caller — interceptor, programmatic
     * {@code UserTransaction} on the same thread — sees the same
     * suspended chain.
     */
    private static final ThreadLocal<Deque<Transaction>> SUSPENDED =
            ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * Marker emitted on {@link TransactionStarted} /
     * {@link TransactionCommitted} / {@link TransactionRolledBack} /
     * {@link TransactionBeforeCompletion} payloads. JTA's transaction is
     * global — the {@code persistenceUnitName} field has no
     * RESOURCE_LOCAL meaning here, so the empty string signals
     * "transaction-wide" to observers that route on it.
     */
    private static final String TRANSACTION_WIDE = "";

    private volatile boolean initialized;

    private volatile TransactionManagerProvider provider;

    private volatile TransactionManager transactionManager;

    private volatile UserTransaction userTransaction;

    /** No-arg constructor required by {@link ServiceLoader}. */
    public JtaTransactionStrategy() {
    }

    @Override
    public synchronized void initialize(Map<String, Object> entityManagerFactoryProperties) {
        if (initialized) {
            throw new IllegalStateException("TransactionStrategy already initialized");
        }
        initialized = true;
        // Provider resolution is deliberately lazy — deferred to the
        // first begin() so the TM bootstrap cost is not paid during
        // CDI BeforeBeanDiscovery (where every TransactionStrategy is
        // queried only for getTransactionType()).
    }

    @Override
    public void begin() {
        TransactionManager tm = ensureProviderResolved();
        try {
            int currentStatus = tm.getStatus();
            if (currentStatus != Status.STATUS_NO_TRANSACTION) {
                // Nested @Transactional: suspend the outer transaction,
                // stash it on the per-thread deque, then begin the
                // inner. Outer resumes after inner's commit / rollback
                // returns.
                Transaction suspended = tm.suspend();
                SUSPENDED.get().push(suspended);
            }
            tm.begin();
        } catch (NotSupportedException | SystemException failure) {
            throw new IllegalStateException("Failed to begin JTA transaction", failure);
        }
        fireEvent(new TransactionStarted(TRANSACTION_WIDE));
    }

    @Override
    public void commit() {
        TransactionManager tm = requireInitialized();
        fireEvent(new TransactionBeforeCompletion(TRANSACTION_WIDE));
        try {
            int status = tm.getStatus();
            if (status == Status.STATUS_MARKED_ROLLBACK) {
                // Mirror RESOURCE_LOCAL behaviour: explicit rollback +
                // RollbackException so the TransactionStrategy contract
                // is identical across strategies. The thrown exception
                // is jakarta.persistence.RollbackException (matching
                // the SPI signature), not jakarta.transaction.RollbackException.
                tm.rollback();
                fireEvent(new TransactionRolledBack(TRANSACTION_WIDE));
                throw new RollbackException("Transaction marked rollback-only");
            }
            tm.commit();
            fireEvent(new TransactionCommitted(TRANSACTION_WIDE));
        } catch (jakarta.transaction.RollbackException rolledByTm) {
            // TM.commit() may roll back internally (e.g. the tx was
            // marked rollback-only between our pre-check and TM.commit).
            // Convert the JTA-checked RollbackException to the
            // persistence one to keep the SPI signature consistent.
            fireEvent(new TransactionRolledBack(TRANSACTION_WIDE));
            RollbackException wrapped = new RollbackException("JTA TM rolled back during commit");
            wrapped.initCause(rolledByTm);
            throw wrapped;
        } catch (HeuristicMixedException | HeuristicRollbackException heuristic) {
            fireEvent(new TransactionRolledBack(TRANSACTION_WIDE));
            throw new IllegalStateException("Heuristic outcome on JTA commit", heuristic);
        } catch (SystemException | SecurityException sysFailure) {
            throw new IllegalStateException("JTA commit failure", sysFailure);
        } finally {
            resumeSuspendedIfAny(tm);
        }
    }

    @Override
    public void rollback() {
        TransactionManager tm = requireInitialized();
        fireEvent(new TransactionBeforeCompletion(TRANSACTION_WIDE));
        try {
            tm.rollback();
            fireEvent(new TransactionRolledBack(TRANSACTION_WIDE));
        } catch (SystemException | SecurityException | IllegalStateException sysFailure) {
            throw new IllegalStateException("JTA rollback failure", sysFailure);
        } finally {
            resumeSuspendedIfAny(tm);
        }
    }

    @Override
    public boolean isActive() {
        TransactionManager tm = transactionManager;
        if (tm == null) {
            return false;
        }
        try {
            int status = tm.getStatus();
            return status == Status.STATUS_ACTIVE || status == Status.STATUS_MARKED_ROLLBACK;
        } catch (SystemException ignored) {
            return false;
        }
    }

    @Override
    public void setRollbackOnly() {
        TransactionManager tm = requireInitialized();
        try {
            tm.setRollbackOnly();
        } catch (SystemException sysFailure) {
            throw new IllegalStateException("JTA setRollbackOnly failure", sysFailure);
        }
    }

    @Override
    public boolean getRollbackOnly() {
        TransactionManager tm = requireInitialized();
        try {
            return tm.getStatus() == Status.STATUS_MARKED_ROLLBACK;
        } catch (SystemException sysFailure) {
            throw new IllegalStateException("JTA getStatus failure", sysFailure);
        }
    }

    @Override
    public TransactionManager getTransactionManager() {
        return ensureProviderResolved();
    }

    @Override
    public UserTransaction userTransaction() {
        ensureProviderResolved();
        return userTransaction;
    }

    @Override
    public PersistenceUnitTransactionType getTransactionType() {
        return PersistenceUnitTransactionType.JTA;
    }

    @Override
    public void shutdown() {
        TransactionManagerProvider currentProvider = provider;
        if (currentProvider == null) {
            return;
        }
        try {
            currentProvider.shutdown();
        } catch (RuntimeException loggedAndIgnored) {
            // Idempotent; never propagated. JVM shutdown hooks and
            // launcher-session listeners must not break on residual
            // cleanup failures.
            LOG.log(Level.WARNING,
                    "TransactionManagerProvider '" + currentProvider.name() + "' shutdown failed",
                    loggedAndIgnored);
        }
        SUSPENDED.remove();
    }

    private TransactionManager ensureProviderResolved() {
        TransactionManager localTm = transactionManager;
        if (localTm != null) {
            return localTm;
        }
        synchronized (this) {
            if (transactionManager != null) {
                return transactionManager;
            }
            TransactionManagerProvider chosen = pickAvailableProvider();
            this.provider = chosen;
            this.transactionManager = chosen.create();
            this.userTransaction = chosen.userTransaction();
            LOG.log(Level.INFO,
                    "JTA TransactionManager bootstrapped via provider '" + chosen.name() + "'");
            return transactionManager;
        }
    }

    /**
     * Walk the priority-sorted candidate list and return the first
     * provider whose {@link TransactionManagerProvider#isAvailable()}
     * returns {@code true}. The priority head is asked first; only when
     * its classes are absent does the strategy fall through to the next
     * candidate. This keeps the project's "lowest priority wins" rule
     * intact while still allowing graceful degradation when the leading
     * provider's runtime classes are missing (typical for the JTA test
     * matrix where one of three impls is on the classpath at a time).
     */
    private static TransactionManagerProvider pickAvailableProvider() {
        ServicePriorityResolver resolver = TestContext.loadService(ServicePriorityResolver.class);
        java.util.ArrayList<TransactionManagerProvider> candidates = new java.util.ArrayList<>();
        for (TransactionManagerProvider candidate : ServiceLoader.load(TransactionManagerProvider.class)) {
            candidates.add(candidate);
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    "No TransactionManagerProvider on the classpath. jta-module is on the dependency "
                            + "tree but no provider impl was discovered via META-INF/services.");
        }
        List<TransactionManagerProvider> sorted = resolver.sort(candidates);
        for (TransactionManagerProvider candidate : sorted) {
            if (candidate.isAvailable()) {
                return candidate;
            }
        }
        StringBuilder names = new StringBuilder();
        for (TransactionManagerProvider candidate : sorted) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(candidate.name());
        }
        throw new IllegalStateException(
                "No TransactionManagerProvider available on the classpath. Tried: " + names);
    }

    private TransactionManager requireInitialized() {
        TransactionManager tm = transactionManager;
        if (tm == null) {
            throw new IllegalStateException(
                    "JtaTransactionStrategy not initialised — begin() must be called before this method.");
        }
        return tm;
    }

    private static void resumeSuspendedIfAny(TransactionManager tm) {
        Deque<Transaction> stack = SUSPENDED.get();
        if (stack.isEmpty()) {
            // No outer to resume; clear the ThreadLocal to avoid
            // pinning the empty deque on the calling thread for the
            // rest of its lifetime.
            SUSPENDED.remove();
            return;
        }
        Transaction outer = stack.pop();
        try {
            tm.resume(outer);
        } catch (jakarta.transaction.InvalidTransactionException
                 | IllegalStateException | SystemException resumeFailure) {
            throw new IllegalStateException("JTA resume failure on suspended outer transaction",
                    resumeFailure);
        }
    }

    private static void fireEvent(Object event) {
        try {
            BeanManager beanManager = CDI.current().getBeanManager();
            beanManager.getEvent().fire(event);
        } catch (RuntimeException ignored) {
            // CDI not up or observer threw — events are non-critical
            // and observer failures are aggregated by the framework.
        }
    }
}
