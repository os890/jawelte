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

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
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
 * <p>The COMMIT flush mode covers the annotated method's transaction
 * and everything called below it: EntityManagers present at entry are
 * swapped here, and EntityManagers lazily created during the body
 * (e.g. a lazily-joined PU, or a nested transaction) are born with
 * {@code COMMIT} via {@code TransactionScopedEmHolder.peekOrAutoBegin}
 * because this interceptor marks the read-only scope for the body's
 * duration. All of them are restored on exit (see
 * {@link #restoreFlushModes(Map)}), so an <em>enclosing</em> scope is
 * never left read-only.
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
 * {@code finally} block. Because every {@code @Transactional}
 * invocation starts a fresh transaction with its own
 * {@link EntityManager}, a genuinely nested {@code @ReadOnly} call
 * operates on its own frame: it swaps and restores its own EM and
 * marks its own transaction rollback-only, independently of the
 * enclosing {@code @ReadOnly} level.
 *
 * <p>The read-only scope is tracked by depth, not a flag
 * ({@link TransactionScopedEmHolder#enterReadOnlyScope()} /
 * {@link TransactionScopedEmHolder#exitReadOnlyScope()}): a nested
 * {@code @ReadOnly} returning does not end the scope — only the
 * outermost level unwinding does. So lazily-joined PUs created after
 * a nested {@code @ReadOnly} has returned are still born read-only,
 * and the enclosing scope is restored only when the outermost level
 * exits.
 *
 * <p>A call-site guard ({@link #CALL_STACK}) shields against double
 * registration (programmatic + auto-discovery) on the same call
 * site: when the interceptor fires a second time for the <em>same</em>
 * {@link Method} already at the top of the per-thread stack, the
 * inner fire simply proceeds without re-applying the flush-mode swap,
 * re-entering the read-only scope, or scheduling a second
 * {@code setRollbackOnly}. Genuinely nested {@code @ReadOnly}
 * invocations between distinct methods still nest correctly because
 * each level pushes a different {@code Method} onto the stack — the
 * same mechanism {@code TransactionalInterceptor} uses.
 */
@Interceptor
@ReadOnly
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 201)
public class ReadOnlyInterceptor {

    /**
     * Per-thread stack of currently-active intercepted {@link Method}
     * frames. The top carries the {@code Method} the current
     * {@code @ReadOnly} invocation is dispatching; a second fire whose
     * {@code Method} matches the top is a duplicate registration on the
     * same call site and short-circuits to
     * {@link InvocationContext#proceed()}. Distinct nested
     * {@code @ReadOnly} methods push distinct {@code Method}s and nest.
     */
    private static final ThreadLocal<Deque<Method>> CALL_STACK = ThreadLocal.withInitial(ArrayDeque::new);

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
        Deque<Method> callStack = CALL_STACK.get();
        Method currentMethod = invocationContext.getMethod();
        if (!callStack.isEmpty() && callStack.peek() == currentMethod) {
            // Duplicate fire on the same call site (programmatic
            // registration + auto-discovery): run the body once.
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
        callStack.push(currentMethod);
        // Enter the read-only scope (by depth) so EntityManagers lazily
        // created during the body (this tx and everything called below it)
        // are also born with FlushModeType.COMMIT — see
        // TransactionScopedEmHolder.peekOrAutoBegin. A nested @ReadOnly
        // returning does not end the scope; only the outermost level does.
        TransactionScopedEmHolder.enterReadOnlyScope();
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
            TransactionScopedEmHolder.exitReadOnlyScope();
            restoreFlushModes(originalFlushModes);
            callStack.pop();
            if (callStack.isEmpty()) {
                CALL_STACK.remove();
            }
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
        // Restore every EM currently on this thread's stack for an active PU,
        // not just the ones swapped at entry: an EM lazily created during the
        // read-only body (not in originalFlushModes) was born AUTO and switched
        // to COMMIT by TransactionScopedEmHolder.peekOrAutoBegin, so it must be
        // reset to AUTO too — otherwise an enclosing scope that shares the tx
        // (REQUIRED) would inherit the read-only flush mode.
        for (String persistenceUnitName : JpaActivePersistenceUnits.get()) {
            EntityManager entityManager = TransactionScopedEmHolder.peek(persistenceUnitName);
            if (entityManager == null || !entityManager.isOpen()) {
                continue;
            }
            FlushModeType restoreTo = originalFlushModes.getOrDefault(persistenceUnitName, FlushModeType.AUTO);
            try {
                entityManager.setFlushMode(restoreTo);
            } catch (RuntimeException ignored) {
                // EM may already be in a state where setFlushMode is
                // disallowed (e.g. mid-completion); ignore — the EM
                // is about to close anyway.
            }
        }
    }
}
