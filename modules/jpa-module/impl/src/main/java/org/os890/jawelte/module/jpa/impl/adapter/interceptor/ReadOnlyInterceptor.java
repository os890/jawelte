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
package org.os890.jawelte.module.jpa.impl.adapter.interceptor;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.ReadOnly;
import org.os890.jawelte.module.jpa.api.port.TransactionStrategy;
import org.os890.jawelte.module.jpa.impl.util.JpaActivePersistenceUnits;
import org.os890.jawelte.module.jpa.impl.util.TransactionScopedEmHolder;

/**
 * CDI interceptor bound to {@link ReadOnly}. When a transaction is
 * active on the calling thread, switches every active
 * {@link EntityManager}'s flush mode to
 * {@link FlushModeType#COMMIT} (so dirty checks do not auto-flush)
 * and marks the transaction rollback-only on
 * {@code aroundInvoke} return — net effect: any
 * {@code em.persist(...)} calls inside the annotated method are
 * discarded.
 *
 * <p>When no transaction is active (e.g. {@code @ReadOnly}
 * declared without {@code @Transactional}), the interceptor still
 * fires but is a documented no-op: the body proceeds and any
 * writes are committed normally.
 *
 * <p>{@code @Priority(Interceptor.Priority.PLATFORM_BEFORE + 201)}
 * places this interceptor inner of {@link TransactionalInterceptor}
 * (priority {@code +200}) so the transaction is already started
 * when the read-only setup runs.
 *
 * <p>The interceptor captures each touched
 * {@link EntityManager}'s original {@link FlushModeType} before
 * switching to {@code COMMIT} and restores it in the
 * {@code finally} block, so a nested {@code @ReadOnly} call does
 * not leave its enclosing level with the wrong flush mode.
 *
 * <p>A re-entrance guard ({@link #ACTIVE}) shields against double
 * registration (programmatic + auto-discovery) on the same call
 * site: when the interceptor fires a second time on the same thread
 * before the first invocation has unwound, the inner call simply
 * proceeds without re-applying the flush-mode swap or scheduling a
 * second {@code setRollbackOnly}. Without this guard the inner
 * call's {@code finally} block would restore the flush mode to
 * what it was before the first invocation captured it — mid-method.
 */
@Interceptor
@ReadOnly
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 201)
public class ReadOnlyInterceptor {

    /**
     * Per-thread re-entrance flag. {@code true} while a
     * {@code @ReadOnly} invocation is mid-flight on the calling
     * thread; the interceptor short-circuits to
     * {@link InvocationContext#proceed()} on any nested fire.
     */
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** No-arg constructor required by CDI. */
    public ReadOnlyInterceptor() {
    }

    /**
     * The interceptor entry point. See class-level Javadoc for the
     * behaviour matrix.
     *
     * @param invocationContext the CDI invocation context
     * @return the value returned by the intercepted method
     * @throws Exception if the intercepted method throws
     */
    @AroundInvoke
    public Object aroundInvoke(InvocationContext invocationContext) throws Exception {
        if (Boolean.TRUE.equals(ACTIVE.get())) {
            return invocationContext.proceed();
        }
        TransactionStrategy strategy = TestContext.loadService(TransactionStrategy.class);
        if (!strategy.isActive()) {
            return invocationContext.proceed();
        }
        Map<String, FlushModeType> originalFlushModes = new LinkedHashMap<>();
        for (String persistenceUnitName : JpaActivePersistenceUnits.get()) {
            EntityManager entityManager = TransactionScopedEmHolder.peek(persistenceUnitName);
            if (entityManager != null) {
                originalFlushModes.put(persistenceUnitName, entityManager.getFlushMode());
                entityManager.setFlushMode(FlushModeType.COMMIT);
            }
        }
        ACTIVE.set(Boolean.TRUE);
        try {
            Object result = invocationContext.proceed();
            strategy.setRollbackOnly();
            return result;
        } catch (Exception | Error throwable) {
            // Multi-catch with Java's "more precise rethrow": the
            // interceptor's `throws Exception` declaration covers Exception
            // subtypes; Error doesn't need declaration. Both branches
            // collapse onto a single mark-rollback + rethrow.
            markRollbackOnlyAndSuppress(strategy, throwable);
            throw throwable;
        } finally {
            ACTIVE.set(Boolean.FALSE);
            restoreFlushModes(originalFlushModes);
        }
    }

    private static void markRollbackOnlyAndSuppress(TransactionStrategy strategy, Throwable primary) {
        try {
            if (strategy.isActive()) {
                strategy.setRollbackOnly();
            }
        } catch (RuntimeException markFailure) {
            // The intercepted method's throwable remains the primary
            // cause; a setRollbackOnly failure rides along as a
            // suppressed exception so post-mortems see both causes
            // (TICKET-001 aggregation rule).
            primary.addSuppressed(markFailure);
        }
    }

    private static void restoreFlushModes(Map<String, FlushModeType> originalFlushModes) {
        for (Map.Entry<String, FlushModeType> entry : originalFlushModes.entrySet()) {
            EntityManager entityManager = TransactionScopedEmHolder.peek(entry.getKey());
            if (entityManager == null || !entityManager.isOpen()) {
                continue;
            }
            try {
                entityManager.setFlushMode(entry.getValue());
            } catch (RuntimeException ignored) {
                // EM may already be in a state where setFlushMode is
                // disallowed (e.g. mid-completion); ignore — the EM
                // is about to close anyway.
            }
        }
    }
}
