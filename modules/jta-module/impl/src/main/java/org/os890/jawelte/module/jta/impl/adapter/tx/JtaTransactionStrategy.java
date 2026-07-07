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
package org.os890.jawelte.module.jta.impl.adapter.tx;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.WeakHashMap;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.persistence.PersistenceUnitTransactionType;
import jakarta.persistence.RollbackException;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
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
// @Dependent makes JtaTransactionStrategy a CDI bean-archive marker —
// without at least one annotated bean class, Weld may skip
// jta-module/impl's beans.xml entirely under
// bean-discovery-mode="annotated". @Dependent is the lightest CDI
// scope (no normal-scope proxy is generated) and the strategy itself
// is never @Inject'd — TestContext.loadService(...) resolves the
// ServiceLoader-instantiated singleton at JVM-static scope. The CDI
// instance and the ServiceLoader instance are independent and the
// CDI one is never observed; the annotation is purely the
// bean-archive marker that pulls the rest of the META-INF wiring
// (META-INF/services, beans.xml) onto Weld's radar.
@Dependent
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
     * Per-{@link Transaction} marker tracking whether the lifecycle
     * events ({@link TransactionStarted} +
     * {@link TransactionBeforeCompletion} +
     * {@link TransactionCommitted} / {@link TransactionRolledBack})
     * have already been wired for the active tx. Two firing paths
     * exist:
     *
     * <ul>
     *   <li><strong>Direct path</strong> — when this strategy's
     *       {@link #begin()} / {@link #commit()} / {@link #rollback()}
     *       drive the JTA tx (jpa-module's own
     *       {@code @Transactional} interceptor or the JUnit
     *       lifecycle adapter). The strategy fires events inline and
     *       writes the marker to suppress the sync-driven path.</li>
     *   <li><strong>Sync path</strong> — when a vendor's
     *       {@code @Transactional} interceptor (Narayana / Quarkus)
     *       drives the tx. {@link #bindLifecycleEventsToCurrentTransaction()}
     *       registers a JTA {@link Synchronization} that fires
     *       {@code TransactionBeforeCompletion} from
     *       {@code beforeCompletion()} and
     *       {@code TransactionCommitted} / {@code TransactionRolledBack}
     *       from {@code afterCompletion(int)}. The same call fires
     *       {@code TransactionStarted} synchronously and sets the
     *       marker.</li>
     * </ul>
     *
     * <p>The marker is cleared on tx completion — the direct path
     * removes it in {@code commit()} / {@code rollback()}, the sync path
     * in the {@code Synchronization}'s {@code afterCompletion(int)} — so
     * correctness depends on completion, not GC timing. A vendor that
     * reuses / pools {@code Transaction} objects (identity-based
     * {@code equals} / {@code hashCode}) therefore never carries a stale
     * marker into the next tx, which would otherwise make the sync path
     * short-circuit and silently drop that tx's lifecycle events.
     * {@link WeakHashMap} remains as a backstop so any entry missed by
     * the completion hooks (e.g. a tx that never completes) is still
     * reclaimed once its {@code Transaction} is GC'd.
     */
    private static final Map<Transaction, Boolean> EVENTS_BOUND =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Marker emitted on {@link TransactionStarted} /
     * {@link TransactionCommitted} / {@link TransactionRolledBack} /
     * {@link TransactionBeforeCompletion} payloads. JTA's transaction is
     * global — the {@code persistenceUnitName} field has no
     * RESOURCE_LOCAL meaning here, so the empty string signals
     * "transaction-wide" to observers that route on it.
     */
    private static final String TRANSACTION_WIDE = "";

    /**
     * JVM-static caches. {@link java.util.ServiceLoader} returns a
     * fresh strategy instance per {@code TestContext.loadService(...)}
     * call (the project's {@code ServicePriorityResolver} pattern), so
     * an instance-level cache here would force every fresh call to
     * re-resolve the provider and bootstrap a new {@code TransactionManager}.
     * Pinning to JVM scope ensures every strategy lookup — including
     * the ones that come through the {@code EntityManagerProxy}'s
     * {@code peekOrAutoBegin} fast path and the
     * {@code StandaloneJtaPlatform.locateTransactionManager} indirection
     * — sees the same TM the {@code @Transactional} interceptor's
     * {@code begin()} drove.
     */
    private static volatile boolean initialized;

    private static volatile TransactionManagerProvider provider;

    private static volatile TransactionManager transactionManager;

    private static volatile UserTransaction userTransaction;

    private static final Object STRATEGY_LOCK = new Object();

    /** No-arg constructor required by {@link ServiceLoader}. */
    public JtaTransactionStrategy() {
    }

    @Override
    public void initialize(Map<String, Object> entityManagerFactoryProperties) {
        synchronized (STRATEGY_LOCK) {
            if (initialized) {
                // initialized at JVM scope rather than per-instance:
                // CDI bootstrap fires once per test class and goes
                // through a fresh strategy instance, but the
                // jta-module/impl invariant is that one TM serves the
                // entire JVM. A second call from a different instance
                // is therefore a no-op rather than a failure.
                return;
            }
            initialized = true;
        }
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
            // Mark events as bound for the freshly-begun tx so the
            // sync-driven path (bindLifecycleEventsToCurrentTx) won't
            // double-fire when an EM is later acquired in this same tx.
            Transaction current = tm.getTransaction();
            if (current != null) {
                EVENTS_BOUND.put(current, Boolean.TRUE);
            }
        } catch (NotSupportedException | SystemException failure) {
            throw new IllegalStateException("Failed to begin JTA transaction", failure);
        }
        fireEvent(new TransactionStarted(TRANSACTION_WIDE));
    }

    @Override
    public void commit() {
        TransactionManager tm = requireInitialized();
        // Capture the completing tx before TM.commit()/rollback() nulls
        // it out, so its EVENTS_BOUND marker can be cleared on completion
        // (see finally). Tying reclamation to completion rather than GC
        // keeps a reused/pooled Transaction object from carrying a stale
        // marker into the next tx on the sync path.
        Transaction completing = currentTransactionQuietly(tm);
        fireEvent(new TransactionBeforeCompletion(TRANSACTION_WIDE));
        // Hold the outcome instead of throwing inline, so a resume failure
        // in the finally can be attached to it (addSuppressed) rather than
        // replacing it — the original commit cause must survive.
        Throwable primary = null;
        try {
            if (tm.getStatus() == Status.STATUS_MARKED_ROLLBACK) {
                // Mirror RESOURCE_LOCAL behaviour: explicit rollback +
                // RollbackException so the TransactionStrategy contract
                // is identical across strategies. The exception is
                // jakarta.persistence.RollbackException (matching the SPI
                // signature), not jakarta.transaction.RollbackException.
                tm.rollback();
                fireEvent(new TransactionRolledBack(TRANSACTION_WIDE));
                primary = new RollbackException("Transaction marked rollback-only");
            } else {
                tm.commit();
                fireEvent(new TransactionCommitted(TRANSACTION_WIDE));
            }
        } catch (jakarta.transaction.RollbackException rolledByTm) {
            // TM.commit() may roll back internally (e.g. the tx was
            // marked rollback-only between our pre-check and TM.commit).
            // Convert the JTA-checked RollbackException to the
            // persistence one to keep the SPI signature consistent.
            fireEvent(new TransactionRolledBack(TRANSACTION_WIDE));
            RollbackException wrapped = new RollbackException("JTA TM rolled back during commit");
            wrapped.initCause(rolledByTm);
            primary = wrapped;
        } catch (HeuristicMixedException | HeuristicRollbackException heuristic) {
            fireEvent(new TransactionRolledBack(TRANSACTION_WIDE));
            primary = new IllegalStateException("Heuristic outcome on JTA commit", heuristic);
        } catch (SystemException | SecurityException sysFailure) {
            primary = new IllegalStateException("JTA commit failure", sysFailure);
        } finally {
            clearEventsBound(completing);
            primary = resumeSuspendedIfAny(tm, primary);
        }
        if (primary != null) {
            throwUnchecked(primary);
        }
    }

    @Override
    public void rollback() {
        TransactionManager tm = requireInitialized();
        Transaction completing = currentTransactionQuietly(tm);
        fireEvent(new TransactionBeforeCompletion(TRANSACTION_WIDE));
        Throwable primary = null;
        try {
            tm.rollback();
            fireEvent(new TransactionRolledBack(TRANSACTION_WIDE));
        } catch (SystemException | SecurityException | IllegalStateException sysFailure) {
            primary = new IllegalStateException("JTA rollback failure", sysFailure);
        } finally {
            clearEventsBound(completing);
            primary = resumeSuspendedIfAny(tm, primary);
        }
        if (primary != null) {
            throwUnchecked(primary);
        }
    }

    @Override
    public boolean isActive() {
        // Resolve through the lazy bootstrap so a fresh strategy
        // instance (ServiceLoader returns one per loadService call)
        // doesn't read a null transactionManager and report inactive
        // even when the JVM-singleton TM has an active transaction.
        TransactionManager tm = ensureProviderResolved();
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
        TransactionManagerProvider currentProvider;
        synchronized (STRATEGY_LOCK) {
            currentProvider = provider;
        }
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

    private static TransactionManager ensureProviderResolved() {
        TransactionManager localTm = transactionManager;
        if (localTm != null) {
            return localTm;
        }
        synchronized (STRATEGY_LOCK) {
            if (transactionManager != null) {
                return transactionManager;
            }
            TransactionManagerProvider chosen = pickAvailableProvider();
            provider = chosen;
            transactionManager = chosen.create();
            userTransaction = chosen.userTransaction();
            // Bind the chosen provider's artifacts into JNDI so vendor
            // CDI integrations (Narayana, future Quarkus) that look up
            // TransactionManager / UserTransaction / TSR via JNDI
            // resolve them to *this* provider's instances — works
            // uniformly whether the active TM is Geronimo, Narayana
            // or something else.
            org.os890.jawelte.module.jta.impl.adapter.jndi.JndiArtifactBinder.bind(chosen);
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

    private static TransactionManager requireInitialized() {
        TransactionManager tm = transactionManager;
        if (tm == null) {
            throw new IllegalStateException(
                    "JtaTransactionStrategy not initialised — begin() must be called before this method.");
        }
        return tm;
    }

    /**
     * Resume the suspended outer transaction (nested {@code @Transactional})
     * after the inner tx has completed. Never throws: any resume failure is
     * returned folded into {@code primary} so it can't mask the inner tx's
     * own commit/rollback failure — if {@code primary} is {@code null} the
     * resume failure becomes the returned exception (thrown as-is by the
     * caller via {@link #throwUnchecked(Throwable)}), otherwise it rides
     * along as a suppressed exception.
     *
     * @param tm      the JVM-singleton {@link TransactionManager}
     * @param primary the inner tx's in-flight exception, or {@code null}
     * @return the throwable the caller should raise, or {@code null}
     */
    private static Throwable resumeSuspendedIfAny(TransactionManager tm, Throwable primary) {
        Deque<Transaction> stack = SUSPENDED.get();
        if (stack.isEmpty()) {
            // No outer to resume; clear the ThreadLocal to avoid
            // pinning the empty deque on the calling thread for the
            // rest of its lifetime.
            SUSPENDED.remove();
            return primary;
        }
        Transaction outer = stack.pop();
        Throwable resumeFailure = tryResume(tm, outer);
        if (resumeFailure == null) {
            return primary;
        }
        if (primary == null) {
            return resumeFailure;
        }
        primary.addSuppressed(resumeFailure);
        return primary;
    }

    private static Throwable tryResume(TransactionManager tm, Transaction outer) {
        int status;
        try {
            status = tm.getStatus();
        } catch (SystemException statusFailure) {
            // Can't determine dissociation; surface the original failure as-is.
            return statusFailure;
        }
        if (status != Status.STATUS_NO_TRANSACTION) {
            // The inner tx is not fully dissociated from the thread (e.g.
            // TM.commit() threw). No resume was attempted, so there is no
            // underlying exception to surface — leave the outer un-resumed and
            // report a clear diagnostic; the caller's primary exception
            // already signals that the transaction is in an indeterminate
            // state the test's cleanup must reset.
            return new IllegalStateException(
                    "JTA resume aborted — inner transaction still associated with the thread (status="
                            + status + "); suspended outer transaction was not resumed");
        }
        try {
            tm.resume(outer);
            return null;
        } catch (jakarta.transaction.InvalidTransactionException
                 | IllegalStateException | SystemException resumeFailure) {
            // Surface the resume failure as-is — thrown directly (via
            // throwUnchecked) when it is the only failure, rather than wrapped.
            return resumeFailure;
        }
    }

    /**
     * Rethrow {@code throwable} unchanged using the generic-erasure
     * "sneaky throw" idiom (as {@code JpaLifecycleAdapter} does), so
     * {@link #commit()} / {@link #rollback()} can raise the original
     * exception — including a checked JTA resume failure that is the sole
     * failure — without a {@code throws} clause and without wrapping it.
     *
     * @param throwable the throwable to rethrow as-is
     * @param <T>       inferred unchecked type; erasure makes the cast a
     *                  no-op so {@code throwable} is thrown unchanged
     */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwUnchecked(Throwable throwable) throws T {
        throw (T) throwable;
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

    private static Transaction currentTransactionQuietly(TransactionManager tm) {
        try {
            return tm.getTransaction();
        } catch (SystemException ignored) {
            return null;
        }
    }

    private static void clearEventsBound(Transaction transaction) {
        if (transaction != null) {
            // Remove the marker on completion so a vendor that reuses /
            // pools the Transaction object (identity-based equals/hashCode)
            // doesn't carry a stale marker into the next tx — which would
            // make bindLifecycleEventsToCurrentTransaction() short-circuit
            // and silently drop that tx's lifecycle events on the sync path.
            EVENTS_BOUND.remove(transaction);
        }
    }

    /**
     * Ensure {@link TransactionStarted} +
     * {@link TransactionBeforeCompletion} +
     * {@link TransactionCommitted} / {@link TransactionRolledBack}
     * fire for the current JTA transaction even when this strategy's
     * own {@link #begin()} / {@link #commit()} / {@link #rollback()}
     * methods aren't on the call path — i.e., when a vendor
     * {@code @Transactional} interceptor (Narayana today, Quarkus
     * later) drives the tx via {@code UserTransaction} directly.
     *
     * <p>Idempotent: a second call within the same tx is a no-op,
     * so callers (currently {@code TransactionScopedEmHolder} on
     * EM acquisition) can call this freely without coordinating
     * with the strategy's own begin path.
     *
     * <p>Fires {@code TransactionStarted} synchronously and registers
     * a JTA {@link Synchronization} that fires the remaining lifecycle
     * events from {@code beforeCompletion()} /
     * {@code afterCompletion(int)}. On the rollback path, JTA does
     * <em>not</em> invoke {@code beforeCompletion()}; the sync fires
     * {@code TransactionBeforeCompletion} from
     * {@code afterCompletion(int)} in that case so jawelte's contract
     * "the event fires on both commit and rollback paths" holds.
     */
    @Override
    public void bindLifecycleEventsToCurrentTransaction() {
        TransactionManager tm = ensureProviderResolved();
        Transaction transaction;
        try {
            transaction = tm.getTransaction();
        } catch (SystemException probe) {
            return;
        }
        if (transaction == null) {
            return;
        }
        if (EVENTS_BOUND.putIfAbsent(transaction, Boolean.TRUE) != null) {
            return;
        }
        try {
            transaction.registerSynchronization(new LifecycleEventSynchronization(transaction));
        } catch (jakarta.transaction.RollbackException | SystemException registerFailure) {
            EVENTS_BOUND.remove(transaction);
            return;
        }
        fireEvent(new TransactionStarted(TRANSACTION_WIDE));
    }

    /**
     * Fires the remaining lifecycle events from JTA's
     * {@link Synchronization} hooks. Used only when this strategy
     * isn't the one driving begin / commit / rollback (i.e., a
     * vendor's CDI {@code @Transactional} interceptor is in charge).
     */
    private static class LifecycleEventSynchronization implements Synchronization {

        private final Transaction transaction;

        private boolean beforeCompletionFired;

        LifecycleEventSynchronization(Transaction transaction) {
            this.transaction = transaction;
        }

        @Override
        public void beforeCompletion() {
            beforeCompletionFired = true;
            fireEvent(new TransactionBeforeCompletion(TRANSACTION_WIDE));
        }

        @Override
        public void afterCompletion(int status) {
            try {
                if (!beforeCompletionFired) {
                    // JTA spec: rollback path skips beforeCompletion. Fire
                    // here so jawelte's contract holds across commit and
                    // rollback paths.
                    fireEvent(new TransactionBeforeCompletion(TRANSACTION_WIDE));
                }
                if (status == Status.STATUS_COMMITTED) {
                    fireEvent(new TransactionCommitted(TRANSACTION_WIDE));
                } else {
                    fireEvent(new TransactionRolledBack(TRANSACTION_WIDE));
                }
            } finally {
                // Clear the marker now the tx has completed, so a reused
                // Transaction object re-binds (fires events) next time
                // instead of being suppressed by a stale marker.
                clearEventsBound(transaction);
            }
        }
    }
}
