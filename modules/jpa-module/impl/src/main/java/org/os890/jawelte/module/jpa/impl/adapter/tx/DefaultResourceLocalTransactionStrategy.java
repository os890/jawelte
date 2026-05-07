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
import java.util.LinkedHashSet;
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
 * RESOURCE_LOCAL transactions across every active persistence unit
 * for the calling thread. Pushes a fresh
 * {@link EntityManager} (per persistence unit) onto
 * {@link TransactionScopedEmHolder} on {@code begin()}, opens its
 * {@link EntityTransaction}, fires
 * {@link TransactionStarted} for each persistence unit; on
 * {@code commit()} / {@code rollback()}, fires
 * {@link TransactionBeforeCompletion}, then walks the active
 * persistence units, completes each transaction, pops the stack
 * frame, closes the manager, and fires the matching outcome event
 * ({@link TransactionCommitted} or {@link TransactionRolledBack}).
 *
 * <p>Nested {@code @Transactional} invocations push a new frame
 * each call so inner transactions are independent of the outer
 * (each frame gets its own per-PU set, per-PU
 * {@code EntityTransaction} list, and rollback-only flag).
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
        TransactionFrame frame = new TransactionFrame();
        for (String persistenceUnitName : persistenceUnits) {
            EntityManagerFactory emf = EmfCache.getCached(persistenceUnitName)
                    .orElseThrow(() -> new IllegalStateException(
                            "No EntityManagerFactory cached for persistence unit '" + persistenceUnitName + "'."));
            EntityManager entityManager = emf.createEntityManager();
            EntityTransaction transaction = entityManager.getTransaction();
            transaction.begin();
            TransactionScopedEmHolder.push(persistenceUnitName, entityManager);
            frame.persistenceUnits.add(persistenceUnitName);
            fireEvent(new TransactionStarted(persistenceUnitName));
        }
        frames.get().push(frame);
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
        for (String persistenceUnitName : frame.persistenceUnits) {
            fireEvent(new TransactionBeforeCompletion(persistenceUnitName));
        }
        for (String persistenceUnitName : frame.persistenceUnits) {
            EntityManager entityManager = TransactionScopedEmHolder.peek(persistenceUnitName);
            try {
                entityManager.getTransaction().commit();
                fireEvent(new TransactionCommitted(persistenceUnitName));
            } finally {
                TransactionScopedEmHolder.pop(persistenceUnitName);
                entityManager.close();
            }
        }
        frames.get().pop();
    }

    @Override
    public void rollback() {
        TransactionFrame frame = activeFrameOrThrow();
        for (String persistenceUnitName : frame.persistenceUnits) {
            fireEvent(new TransactionBeforeCompletion(persistenceUnitName));
        }
        for (String persistenceUnitName : frame.persistenceUnits) {
            EntityManager entityManager = TransactionScopedEmHolder.peek(persistenceUnitName);
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
        frames.get().pop();
    }

    @Override
    public boolean isActive() {
        return !frames.get().isEmpty();
    }

    @Override
    public void setRollbackOnly() {
        TransactionFrame frame = activeFrameOrThrow();
        frame.rollbackOnly = true;
        for (String persistenceUnitName : frame.persistenceUnits) {
            EntityManager entityManager = TransactionScopedEmHolder.peek(persistenceUnitName);
            entityManager.getTransaction().setRollbackOnly();
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
        try {
            BeanManager beanManager = CDI.current().getBeanManager();
            beanManager.getEvent().fire(event);
        } catch (RuntimeException ignored) {
            // CDI not up or observer threw — events are non-critical
            // and observer failures are aggregated by the framework.
        }
    }

    /**
     * Per-nesting-level state for a single {@code begin()} call:
     * the persistence units that participated, and the rollback-only
     * flag. Independent stacks for nested {@code @Transactional}
     * invocations.
     */
    private static class TransactionFrame {

        private final Set<String> persistenceUnits = new LinkedHashSet<>();

        private boolean rollbackOnly;

        TransactionFrame() {
        }
    }
}
