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
 * Default {@link TransactionManagerProvider} for the JBoss Narayana JTA
 * implementation ({@code org.jboss.narayana.jta:narayana-jta}).
 *
 * <p>{@code @Priority(Integer.MAX_VALUE)} — lowest priority of the
 * shipped defaults; wins only when no other provider is on the classpath
 * (Geronimo at {@code MAX_VALUE - 2} and Atomikos at
 * {@code MAX_VALUE - 1} both win when present).
 *
 * <p>Reflection-only: jta-module/impl never compile-depends on any
 * Narayana class. Consumers add {@code narayana-jta} to their test
 * classpath under the {@code jta-narayana} build profile.
 *
 * <p>Note: jpa-module's {@code JpaCdiExtension} vetos
 * {@code com.arjuna.ats.jta.cdi.*} types during {@code ProcessAnnotatedType}
 * so Narayana's embedded CDI integration (which would otherwise install
 * its own {@code @Transactional} interceptor and a competing
 * {@code TransactionContext}) does not register beans that conflict with
 * jpa-module's wiring. The TM itself is reached via the
 * {@code com.arjuna.ats.jta.TransactionManager} static accessor — it is
 * not a CDI bean and is unaffected by the veto.
 */
@Priority(Integer.MAX_VALUE)
public class NarayanaTransactionManagerProvider implements TransactionManagerProvider {

    private static final String NARAYANA_TM_ACCESSOR_CLASS =
            "com.arjuna.ats.jta.TransactionManager";

    private static final String NARAYANA_USER_TRANSACTION_ACCESSOR_CLASS =
            "com.arjuna.ats.jta.UserTransaction";

    /** No-arg constructor required by {@link ServiceLoader}. */
    public NarayanaTransactionManagerProvider() {
    }

    @Override
    public boolean isAvailable() {
        return loadable(NARAYANA_TM_ACCESSOR_CLASS)
                && loadable(NARAYANA_USER_TRANSACTION_ACCESSOR_CLASS);
    }

    @Override
    public TransactionManager create() {
        try {
            Class<?> accessor = forName(NARAYANA_TM_ACCESSOR_CLASS);
            return (TransactionManager) accessor.getMethod("transactionManager").invoke(null);
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new IllegalStateException(
                    "Failed to obtain Narayana TransactionManager via reflection",
                    reflectionFailure);
        }
    }

    @Override
    public UserTransaction userTransaction() {
        try {
            Class<?> accessor = forName(NARAYANA_USER_TRANSACTION_ACCESSOR_CLASS);
            return (UserTransaction) accessor.getMethod("userTransaction").invoke(null);
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new IllegalStateException(
                    "Failed to obtain Narayana UserTransaction via reflection",
                    reflectionFailure);
        }
    }

    @Override
    public void shutdown() {
        // Narayana's standalone profile holds no JVM-external resources
        // that need explicit release here. Recovery threads and the
        // object store are managed by Narayana's own shutdown hooks.
    }

    @Override
    public String name() {
        return "Narayana";
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
