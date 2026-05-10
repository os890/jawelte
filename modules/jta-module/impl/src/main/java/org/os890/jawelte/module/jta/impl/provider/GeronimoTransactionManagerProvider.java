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
package org.os890.jawelte.module.jta.impl.provider;

import java.util.ServiceLoader;

import jakarta.annotation.Priority;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.UserTransaction;

import org.os890.jawelte.module.jta.api.port.TransactionManagerProvider;

/**
 * Default {@link TransactionManagerProvider} for Apache Geronimo's
 * standalone JTA implementation
 * ({@code org.apache.geronimo.components:geronimo-transaction}).
 *
 * <p>{@code @Priority(Integer.MAX_VALUE - 2)} — wins over Atomikos
 * ({@code MAX_VALUE - 1}) and Narayana ({@code MAX_VALUE}) when more
 * than one is on the classpath. This is the project's chosen default.
 *
 * <p>Reflection-only: jta-module/impl never compile-depends on any
 * Geronimo class. Consumers add {@code geronimo-transaction} to their
 * test classpath under the {@code jta-geronimo} build profile.
 */
@Priority(Integer.MAX_VALUE - 2)
public class GeronimoTransactionManagerProvider implements TransactionManagerProvider {

    private static final String GERONIMO_TM_CLASS =
            "org.apache.geronimo.transaction.manager.GeronimoTransactionManager";

    private static final String GERONIMO_USER_TRANSACTION_CLASS =
            "org.apache.geronimo.transaction.manager.UserTransactionImpl";

    /** No-arg constructor required by {@link ServiceLoader}. */
    public GeronimoTransactionManagerProvider() {
    }

    @Override
    public boolean isAvailable() {
        return loadable(GERONIMO_TM_CLASS) && loadable(GERONIMO_USER_TRANSACTION_CLASS);
    }

    @Override
    public TransactionManager create() {
        try {
            Class<?> transactionManagerClass = forName(GERONIMO_TM_CLASS);
            return (TransactionManager) transactionManagerClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new IllegalStateException(
                    "Failed to instantiate Geronimo TransactionManager via reflection",
                    reflectionFailure);
        }
    }

    @Override
    public UserTransaction userTransaction() {
        // Geronimo's UserTransactionImpl wraps the TransactionManager and
        // exposes the standard UserTransaction surface. We rebuild a TM
        // here rather than caching one because the strategy is the
        // canonical owner of the TM lifecycle; this method is called once
        // per JVM via JtaTransactionStrategy.ensureProviderResolved.
        TransactionManager transactionManager = create();
        try {
            Class<?> userTransactionClass = forName(GERONIMO_USER_TRANSACTION_CLASS);
            return (UserTransaction) userTransactionClass
                    .getDeclaredConstructor(TransactionManager.class)
                    .newInstance(transactionManager);
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new IllegalStateException(
                    "Failed to instantiate Geronimo UserTransactionImpl via reflection",
                    reflectionFailure);
        }
    }

    @Override
    public void shutdown() {
        // Geronimo's standalone TM holds no resources outside JVM-managed
        // memory; nothing to release. Idempotent no-op.
    }

    @Override
    public String name() {
        return "Geronimo";
    }

    private static boolean loadable(String fullClassName) {
        try {
            forName(fullClassName);
            return true;
        } catch (ClassNotFoundException notLoadable) {
            return false;
        }
    }

    private static Class<?> forName(String fullClassName) throws ClassNotFoundException {
        return Class.forName(fullClassName, false, Thread.currentThread().getContextClassLoader());
    }
}
