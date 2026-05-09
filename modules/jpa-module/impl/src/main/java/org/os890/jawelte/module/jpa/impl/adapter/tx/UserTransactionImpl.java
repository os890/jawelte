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

import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.port.TransactionStrategy;

/**
 * {@link UserTransaction} implementation that delegates to the
 * active {@link TransactionStrategy}. Registered as a synthetic
 * CDI bean by {@code JpaCdiExtension.afterBeanDiscovery}; users
 * inject it via {@code @Inject UserTransaction} for programmatic
 * transaction boundaries.
 *
 * <p>Composition with {@code @Transactional}: a
 * {@code userTx.begin()} call inside an active
 * {@code @Transactional} pushes a fresh frame onto the
 * strategy's per-thread frame stack — same shape as nested
 * {@code @Transactional} (independent transactions, not real
 * nesting).
 *
 * <p>Stateless. Resolves the active {@link TransactionStrategy}
 * lazily on every call via
 * {@code TestContext.loadService(TransactionStrategy.class)} so
 * a consumer-shipped strategy at a lower {@code @Priority} takes
 * over without re-injection.
 */
public class UserTransactionImpl implements UserTransaction {

    /** No-arg constructor used by {@code JpaCdiExtension.produceWith}. */
    public UserTransactionImpl() {
    }

    @Override
    public void begin() {
        strategy().begin();
    }

    @Override
    public void commit() {
        strategy().commit();
    }

    @Override
    public void rollback() {
        strategy().rollback();
    }

    @Override
    public void setRollbackOnly() {
        strategy().setRollbackOnly();
    }

    @Override
    public int getStatus() {
        TransactionStrategy strategy = strategy();
        if (!strategy.isActive()) {
            return Status.STATUS_NO_TRANSACTION;
        }
        if (strategy.getRollbackOnly()) {
            return Status.STATUS_MARKED_ROLLBACK;
        }
        return Status.STATUS_ACTIVE;
    }

    @Override
    public void setTransactionTimeout(int seconds) {
        // RESOURCE_LOCAL has no native transaction-timeout concept;
        // the standard EntityTransaction API does not expose one.
        // jpa-module accepts the call as a no-op so that user code
        // written against the standard UserTransaction interface
        // still compiles and runs.
    }

    private static TransactionStrategy strategy() {
        return TestContext.loadService(TransactionStrategy.class);
    }
}
