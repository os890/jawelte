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
package org.os890.jawelte.module.jta.impl.hibernate;

import jakarta.transaction.TransactionManager;
import jakarta.transaction.UserTransaction;

import org.hibernate.engine.transaction.jta.platform.internal.AbstractJtaPlatform;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.port.TransactionStrategy;

/**
 * Standalone Hibernate {@code JtaPlatform} — locates the JTA
 * {@link TransactionManager} and {@link UserTransaction} via the active
 * {@link TransactionStrategy} rather than via JNDI. Selected through the
 * EMF property
 * {@code hibernate.transaction.jta.platform=org.os890.jawelte.module.jta.impl.hibernate.StandaloneJtaPlatform}
 * contributed by {@code JtaPersistencePropertyResolver} when the JTA
 * strategy is active.
 *
 * <p>Hibernate instantiates this class once per
 * {@code EntityManagerFactory} on first {@code EntityManager} use, then
 * reuses the resolved {@code TransactionManager} via
 * {@link AbstractJtaPlatform#retrieveTransactionManager()} for the
 * lifetime of the EMF — so the {@code TestContext.loadService} cost is
 * paid once per PU, not once per query.
 */
public class StandaloneJtaPlatform extends AbstractJtaPlatform {

    private static final long serialVersionUID = 1L;

    /** No-arg constructor required by Hibernate's strategy selector. */
    public StandaloneJtaPlatform() {
    }

    @Override
    protected TransactionManager locateTransactionManager() {
        return TestContext.loadService(TransactionStrategy.class).getTransactionManager();
    }

    @Override
    protected UserTransaction locateUserTransaction() {
        return TestContext.loadService(TransactionStrategy.class).userTransaction();
    }
}
