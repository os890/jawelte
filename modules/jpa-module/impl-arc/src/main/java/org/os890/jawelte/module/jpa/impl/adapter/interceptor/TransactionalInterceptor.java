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

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.persistence.PersistenceUnitTransactionType;
import jakarta.transaction.Transactional;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.port.TransactionStrategy;
import org.os890.jawelte.module.jpa.impl.adapter.context.TransactionScopedContext;
import org.os890.jawelte.module.jpa.impl.util.TransactionScopedEmHolder;

/**
 * CDI interceptor bound to {@link Transactional}. Wraps the method
 * body with a transaction lifecycle driven by the active
 * {@link TransactionStrategy} and activates the
 * {@link TransactionScopedContext} for the duration of the
 * transaction.
 *
 * <p>Rollback rule (project-wide decision, simpler than the
 * Jakarta EE default):
 *
 * <ul>
 *   <li>Method returns normally → commit.</li>
 *   <li>Method throws <em>any</em> exception
 *       ({@link RuntimeException}, checked {@link Exception}, or
 *       {@link Error}) → rollback, then re-throw.</li>
 * </ul>
 *
 * <p>The Jakarta EE convention (commit on checked, rollback on
 * unchecked) is intentionally NOT followed: a thrown exception
 * almost always means "this work should not persist", regardless
 * of whether it's checked. The {@code rollbackOn} /
 * {@code dontRollbackOn} attributes on {@code @Transactional} are
 * accepted on the source level (they are on the standard
 * annotation) but not interpreted by this interceptor.
 *
 * <p>{@code @Priority(Interceptor.Priority.PLATFORM_BEFORE + 200)}
 * places this interceptor outer of the {@code ReadOnlyInterceptor}
 * (priority {@code +201}) so the transaction is already active
 * when read-only setup runs.
 *
 * <p>A duplicate-method guard ({@link #CALL_STACK}) shields against
 * double registration (programmatic + auto-discovery) on the same
 * call site: when the interceptor fires a second time for the
 * <em>same</em> {@link Method} that is already at the top of the
 * per-thread call stack, the inner call simply proceeds without
 * starting a second transaction. Real nested {@code @Transactional}
 * invocations between distinct methods still nest correctly because
 * each level pushes a different {@code Method} onto the stack.
 */
@Interceptor
@Transactional
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 200)
public class TransactionalInterceptor {

    /**
     * Per-thread stack of currently-active intercepted {@link Method}
     * frames. The top of the deque carries the {@code Method} the
     * outer-most {@code @Transactional} invocation is dispatching;
     * a second fire whose {@code Method} matches the top is a
     * duplicate registration and short-circuits to
     * {@link InvocationContext#proceed()}.
     */
    private static final ThreadLocal<Deque<Method>> CALL_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    /** No-arg constructor required by CDI. */
    public TransactionalInterceptor() {
    }

    /**
     * The interceptor entry point. Begins a transaction, activates
     * the {@code @TransactionScoped} context, runs the method,
     * commits or rolls back per the rule above, and deactivates
     * the context unconditionally in {@code finally}.
     *
     * @param invocationContext the CDI invocation context
     * @return the value returned by the intercepted method
     * @throws Exception if the intercepted method throws (the same
     *                   throwable propagates after the rollback /
     *                   commit decision)
     */
    @AroundInvoke
    public Object aroundInvoke(InvocationContext invocationContext) throws Exception {
        Deque<Method> callStack = CALL_STACK.get();
        Method currentMethod = invocationContext.getMethod();
        if (!callStack.isEmpty() && callStack.peek() == currentMethod) {
            // Duplicate fire on the same call site (e.g. programmatic
            // registration + auto-discovery): just run the body once.
            return invocationContext.proceed();
        }
        TransactionStrategy strategy = TestContext.loadService(TransactionStrategy.class);
        TransactionScopedContext transactionScopedContext = TransactionScopedContext.current();
        if (transactionScopedContext == null) {
            // No jawelte-managed TransactionScopedContext is
            // registered — under @QuarkusTest the portable
            // JpaCdiExtension doesn't fire afterBeanDiscovery, so the
            // jpa-module side has nothing to drive. Quarkus's own
            // narayana-jta interceptor already runs higher up the
            // chain and has begun / will commit the transaction; we
            // proceed silently and let it handle the boundary.
            return invocationContext.proceed();
        }
        callStack.push(currentMethod);
        // Under JTA the strategy intentionally doesn't push a frame
        // onto TransactionScopedEmHolder (the holder is jpa-module/impl-
        // private and the JTA strategy never touches it). The
        // interceptor opens an "all-lazy" frame here so that the
        // EntityManagerProxy's peekOrAutoBegin path can lazy-create
        // and join JTA-mode EMs as the @Transactional method body
        // runs. Under RESOURCE_LOCAL the strategy itself opens the
        // frame on begin(), so this branch is skipped to keep the
        // existing behaviour intact.
        boolean jtaModeFrameOwned = strategy.getTransactionType() == PersistenceUnitTransactionType.JTA;
        if (jtaModeFrameOwned) {
            TransactionScopedEmHolder.enterTransactionalScope();
        }
        strategy.begin();
        transactionScopedContext.activate();
        try {
            Object result;
            try {
                result = invocationContext.proceed();
            } catch (Exception | Error throwable) {
                // Multi-catch with Java's "more precise rethrow": Exception
                // subtypes (RuntimeException + checked Exception) and Error
                // collapse onto a single rollback path. The original throwable
                // re-throws unchanged so callers see the exact cause.
                rollbackAndSuppress(strategy, throwable);
                throw throwable;
            }
            if (strategy.getRollbackOnly()) {
                strategy.rollback();
            } else {
                strategy.commit();
            }
            return result;
        } finally {
            transactionScopedContext.deactivate();
            if (jtaModeFrameOwned) {
                TransactionScopedEmHolder.exitTransactionalScope();
            }
            callStack.pop();
            if (callStack.isEmpty()) {
                CALL_STACK.remove();
            }
        }
    }

    private static void rollbackAndSuppress(TransactionStrategy strategy, Throwable primary) {
        if (!strategy.isActive()) {
            return;
        }
        try {
            strategy.rollback();
        } catch (RuntimeException rollbackFailure) {
            // primary failure (the intercepted method's throwable) takes
            // precedence; the rollback failure rides along as a suppressed
            // exception so post-mortems still see both causes (TICKET-001
            // aggregation rule).
            primary.addSuppressed(rollbackFailure);
        }
    }

}
