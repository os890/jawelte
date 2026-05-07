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

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.transaction.Transactional;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.port.TransactionStrategy;
import org.os890.jawelte.module.jpa.impl.adapter.context.TransactionScopedContext;

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
 */
@Interceptor
@Transactional
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 200)
public class TransactionalInterceptor {

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
        TransactionStrategy strategy = TestContext.loadService(TransactionStrategy.class);
        TransactionScopedContext transactionScopedContext = TransactionScopedContext.current();
        if (transactionScopedContext == null) {
            throw new IllegalStateException(
                    "TransactionScopedContext is not registered. Was JpaCdiExtension.afterBeanDiscovery skipped?");
        }
        strategy.begin();
        transactionScopedContext.activate();
        Object result;
        try {
            try {
                result = invocationContext.proceed();
            } catch (RuntimeException unchecked) {
                rollbackAndSuppress(strategy, unchecked);
                throw unchecked;
            } catch (Error error) {
                rollbackAndSuppress(strategy, error);
                throw error;
            } catch (Exception checked) {
                rollbackAndSuppress(strategy, checked);
                throw checked;
            }
            if (strategy.getRollbackOnly()) {
                strategy.rollback();
            } else {
                strategy.commit();
            }
            return result;
        } finally {
            transactionScopedContext.deactivate();
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
