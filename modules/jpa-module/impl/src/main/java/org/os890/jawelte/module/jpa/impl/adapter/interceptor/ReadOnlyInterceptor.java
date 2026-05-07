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
 */
@Interceptor
@ReadOnly
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 201)
public class ReadOnlyInterceptor {

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
        TransactionStrategy strategy = TestContext.loadService(TransactionStrategy.class);
        if (!strategy.isActive()) {
            return invocationContext.proceed();
        }
        for (String persistenceUnitName : JpaActivePersistenceUnits.get()) {
            EntityManager entityManager = TransactionScopedEmHolder.peek(persistenceUnitName);
            if (entityManager != null) {
                entityManager.setFlushMode(FlushModeType.COMMIT);
            }
        }
        try {
            Object result = invocationContext.proceed();
            strategy.setRollbackOnly();
            return result;
        } catch (RuntimeException | Error throwable) {
            try {
                if (strategy.isActive()) {
                    strategy.setRollbackOnly();
                }
            } catch (RuntimeException ignored) {
                // The original throwable remains the primary cause.
            }
            throw throwable;
        } catch (Exception checked) {
            try {
                if (strategy.isActive()) {
                    strategy.setRollbackOnly();
                }
            } catch (RuntimeException ignored) {
                // Same as above for the checked-exception path.
            }
            throw checked;
        }
    }
}
