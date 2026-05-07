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
package org.os890.jawelte.module.jpa.impl.adapter.tx;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.PersistenceUnitTransactionType;
import jakarta.persistence.RollbackException;
import jakarta.transaction.TransactionManager;

import org.os890.jawelte.module.jpa.api.event.TransactionBeforeCompletion;
import org.os890.jawelte.module.jpa.api.event.TransactionCommitted;
import org.os890.jawelte.module.jpa.api.event.TransactionRolledBack;
import org.os890.jawelte.module.jpa.api.event.TransactionStarted;
import org.os890.jawelte.module.jpa.api.port.TransactionStrategy;
import org.os890.jawelte.module.jpa.impl.util.EmfCache;
import org.os890.jawelte.module.jpa.impl.util.JpaActivePersistenceUnits;
import org.os890.jawelte.module.jpa.impl.util.TransactionScopedEmHolder;

/**
 * Default {@link TransactionStrategy} shipped by jpa-module: drives
 * RESOURCE_LOCAL transactions for the calling thread. On
 * {@code begin()} it opens a transaction <em>only</em> on the
 * "managed" persistence unit — the first entry in
 * {@link JpaActivePersistenceUnits} (insertion order, mirrors
 * {@code persistence.xml} order). Non-managed persistence units
 * lazy-join the scope on first {@code EntityManager} dereference
 * via
 * {@link TransactionScopedEmHolder#peekOrAutoBegin(String)}.
 *
 * <p>{@code commit()} and {@code rollback()} read the per-frame PU
 * set from {@link TransactionScopedEmHolder} so they cover both
 * the managed PU and any lazy-joined ones. Per-PU completion fires
 * {@link TransactionBeforeCompletion} once per PU, then either
 * {@link TransactionCommitted} or {@link TransactionRolledBack}.
 *
 * <p>Nested {@code @Transactional} invocations push a new frame so
 * inner transactions are independent of the outer (each frame gets
 * its own per-PU set, per-PU {@code EntityTransaction} list, and
 * rollback-only flag).
 *
 * <p>{@code @Priority(Integer.MAX_VALUE)} so a future jta-module
 * (or any consumer-supplied strategy) can take over by registering
 * an alternative at a lower priority via
 * {@code META-INF/services}.
 */
@Priority(Integer.MAX_VALUE)
public class DefaultResourceLocalTransactionStrategy implements TransactionStrategy {

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private final ThreadLocal<Deque<TransactionFrame>> frames = ThreadLocal.withInitial(ArrayDeque::new);

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public DefaultResourceLocalTransactionStrategy() {
    }

    @Override
    public void initialize(Map<String, Object> entityManagerFactoryProperties) {
        if (!initialized.compareAndSet(false, true)) {
            throw new IllegalStateException("TransactionStrategy already initialized");
        }
    }

    @Override
    public void begin() {
        Set<String> persistenceUnits = JpaActivePersistenceUnits.get();
        if (persistenceUnits.isEmpty()) {
            throw new IllegalStateException(
                    "No active persistence units. Was JpaCdiExtension.beforeBeanDiscovery skipped?");
        }
        String managedPersistenceUnitName = persistenceUnits.iterator().next();
        EntityManagerFactory emf = EmfCache.getCached(managedPersistenceUnitName)
                .orElseThrow(() -> new IllegalStateException(
                        "No EntityManagerFactory cached for persistence unit '"
                                + managedPersistenceUnitName + "'."));
        EntityManager entityManager = emf.createEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();
        transaction.begin();
        TransactionScopedEmHolder.push(managedPersistenceUnitName, entityManager);
        TransactionScopedEmHolder.enterTransactionalScope(managedPersistenceUnitName);
        frames.get().push(new TransactionFrame());
        fireEvent(new TransactionStarted(managedPersistenceUnitName));
    }

    @Override
    public void commit() {
        TransactionFrame frame = activeFrameOrThrow();
        if (frame.rollbackOnly) {
            try {
                rollback();
            } catch (RuntimeException ignored) {
                // commit's checked semantics demand RollbackException;
                // any rollback failure is captured as suppressed.
            }
            throw new RollbackException("Transaction marked rollback-only");
        }
        Set<String> framePersistenceUnits = TransactionScopedEmHolder.currentFramePersistenceUnits();
        for (String persistenceUnitName : framePersistenceUnits) {
            fireEvent(new TransactionBeforeCompletion(persistenceUnitName));
        }
        try {
            // Phase 1: flush every active EM. If any flush fails,
            // roll every PU back (best-effort multi-PU atomicity over
            // independent RESOURCE_LOCAL transactions) and re-throw.
            flushAllOrRollback(framePersistenceUnits);
            // Phase 2: every flush succeeded, commit each EM. Per-PU
            // commit failures aggregate via primary + addSuppressed
            // per TICKET-001; remaining PUs still get the chance to
            // commit so partial failure doesn't leave EMs open.
            commitAllAggregated(framePersistenceUnits);
        } finally {
            frames.get().pop();
            TransactionScopedEmHolder.exitTransactionalScope();
        }
    }

    private void flushAllOrRollback(Set<String> framePersistenceUnits) {
        RuntimeException flushFailure = null;
        for (String persistenceUnitName : framePersistenceUnits) {
            EntityManager entityManager = TransactionScopedEmHolder.peek(persistenceUnitName);
            if (entityManager == null) {
                continue;
            }
            try {
                entityManager.flush();
            } catch (RuntimeException failure) {
                flushFailure = failure;
                break;
            }
        }
        if (flushFailure == null) {
            return;
        }
        for (String persistenceUnitName : framePersistenceUnits) {
            EntityManager entityManager = TransactionScopedEmHolder.peek(persistenceUnitName);
            if (entityManager == null) {
                continue;
            }
            try {
                if (entityManager.getTransaction().isActive()) {
                    entityManager.getTransaction().rollback();
                }
                fireEvent(new TransactionRolledBack(persistenceUnitName));
            } catch (RuntimeException rollbackFailure) {
                flushFailure.addSuppressed(rollbackFailure);
            } finally {
                try {
                    TransactionScopedEmHolder.pop(persistenceUnitName);
                } catch (RuntimeException ignored) {
                    // pop after a rollback that already drained the deque;
                    // primary failure stays in flushFailure.
                }
                try {
                    entityManager.close();
                } catch (RuntimeException closeFailure) {
                    flushFailure.addSuppressed(closeFailure);
                }
            }
        }
        throw flushFailure;
    }

    private void commitAllAggregated(Set<String> framePersistenceUnits) {
        RuntimeException primary = null;
        for (String persistenceUnitName : framePersistenceUnits) {
            EntityManager entityManager = TransactionScopedEmHolder.peek(persistenceUnitName);
            if (entityManager == null) {
                continue;
            }
            try {
                entityManager.getTransaction().commit();
                fireEvent(new TransactionCommitted(persistenceUnitName));
            } catch (RuntimeException commitFailure) {
                if (primary == null) {
                    primary = commitFailure;
                } else {
                    primary.addSuppressed(commitFailure);
                }
            } finally {
                try {
                    TransactionScopedEmHolder.pop(persistenceUnitName);
                } catch (RuntimeException ignored) {
                    // already popped or never pushed; primary keeps its cause.
                }
                try {
                    entityManager.close();
                } catch (RuntimeException closeFailure) {
                    if (primary == null) {
                        primary = closeFailure;
                    } else {
                        primary.addSuppressed(closeFailure);
                    }
                }
            }
        }
        if (primary != null) {
            throw primary;
        }
    }

    @Override
    public void rollback() {
        activeFrameOrThrow();
        Set<String> framePersistenceUnits = TransactionScopedEmHolder.currentFramePersistenceUnits();
        for (String persistenceUnitName : framePersistenceUnits) {
            fireEvent(new TransactionBeforeCompletion(persistenceUnitName));
        }
        try {
            for (String persistenceUnitName : framePersistenceUnits) {
                EntityManager entityManager = TransactionScopedEmHolder.peek(persistenceUnitName);
                if (entityManager == null) {
                    continue;
                }
                try {
                    if (entityManager.getTransaction().isActive()) {
                        entityManager.getTransaction().rollback();
                    }
                    fireEvent(new TransactionRolledBack(persistenceUnitName));
                } finally {
                    TransactionScopedEmHolder.pop(persistenceUnitName);
                    entityManager.close();
                }
            }
        } finally {
            frames.get().pop();
            TransactionScopedEmHolder.exitTransactionalScope();
        }
    }

    @Override
    public boolean isActive() {
        return !frames.get().isEmpty();
    }

    @Override
    public void setRollbackOnly() {
        TransactionFrame frame = activeFrameOrThrow();
        frame.rollbackOnly = true;
        for (String persistenceUnitName : TransactionScopedEmHolder.currentFramePersistenceUnits()) {
            EntityManager entityManager = TransactionScopedEmHolder.peek(persistenceUnitName);
            if (entityManager != null) {
                entityManager.getTransaction().setRollbackOnly();
            }
        }
    }

    @Override
    public boolean getRollbackOnly() {
        return activeFrameOrThrow().rollbackOnly;
    }

    @Override
    public TransactionManager getTransactionManager() {
        return null;
    }

    @Override
    public PersistenceUnitTransactionType getTransactionType() {
        return PersistenceUnitTransactionType.RESOURCE_LOCAL;
    }

    @Override
    public void shutdown() {
        frames.remove();
    }

    private TransactionFrame activeFrameOrThrow() {
        Deque<TransactionFrame> stack = frames.get();
        if (stack.isEmpty()) {
            throw new IllegalStateException("No active transaction");
        }
        return stack.peek();
    }

    private static void fireEvent(Object event) {
        if (!TransactionScopedEmHolder.isFrameworkOwned()) {
            return;
        }
        try {
            BeanManager beanManager = CDI.current().getBeanManager();
            beanManager.getEvent().fire(event);
        } catch (RuntimeException ignored) {
            // CDI not up or observer threw — events are non-critical
            // and observer failures are aggregated by the framework.
        }
    }

    /**
     * Per-nesting-level state for a single {@code begin()} call —
     * just the rollback-only flag now that the per-PU set lives in
     * {@link TransactionScopedEmHolder}'s frame stack. Independent
     * stacks for nested {@code @Transactional} invocations.
     */
    private static class TransactionFrame {

        private boolean rollbackOnly;

        TransactionFrame() {
        }
    }
}
