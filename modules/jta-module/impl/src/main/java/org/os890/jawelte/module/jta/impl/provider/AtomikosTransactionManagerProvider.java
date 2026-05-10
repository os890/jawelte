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

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ServiceLoader;

import jakarta.annotation.Priority;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.UserTransaction;

import org.os890.jawelte.module.jta.api.port.TransactionManagerProvider;

/**
 * Default {@link TransactionManagerProvider} for the Atomikos
 * TransactionsEssentials JTA implementation
 * ({@code com.atomikos:transactions-jta}).
 *
 * <p>{@code @Priority(Integer.MAX_VALUE - 1)} — sits between Geronimo
 * ({@code MAX_VALUE - 2}, the default winner) and Narayana
 * ({@code MAX_VALUE}, the fallback when nothing else is on the
 * classpath). Wins when Geronimo is absent and Atomikos is present.
 *
 * <p>Reflection-only: jta-module/impl never compile-depends on any
 * Atomikos class. Consumers add {@code transactions-jta} to their test
 * classpath under the {@code jta-atomikos} build profile.
 *
 * <p>Atomikos exposes
 * {@code com.atomikos.icatch.jta.UserTransactionManager} as a single
 * object that implements both {@link TransactionManager} and
 * {@link UserTransaction}. This provider caches the same instance for
 * both lookups so the strategy never observes a TM/UT pair pointing at
 * different transaction managers, and calls {@code init()} once before
 * returning either one.
 */
@Priority(Integer.MAX_VALUE - 1)
public class AtomikosTransactionManagerProvider implements TransactionManagerProvider {

    private static final Logger LOG =
            System.getLogger(AtomikosTransactionManagerProvider.class.getName());

    private static final String ATOMIKOS_USER_TRANSACTION_MANAGER_CLASS =
            "com.atomikos.icatch.jta.UserTransactionManager";

    private volatile Object userTransactionManager;

    /** No-arg constructor required by {@link ServiceLoader}. */
    public AtomikosTransactionManagerProvider() {
    }

    @Override
    public boolean isAvailable() {
        return loadable(ATOMIKOS_USER_TRANSACTION_MANAGER_CLASS);
    }

    @Override
    public TransactionManager create() {
        return (TransactionManager) ensureUserTransactionManager();
    }

    @Override
    public UserTransaction userTransaction() {
        return (UserTransaction) ensureUserTransactionManager();
    }

    @Override
    public synchronized void shutdown() {
        Object current = userTransactionManager;
        if (current == null) {
            return;
        }
        try {
            current.getClass().getMethod("close").invoke(current);
        } catch (ReflectiveOperationException loggedAndIgnored) {
            LOG.log(Level.WARNING,
                    "Atomikos UserTransactionManager.close() failed via reflection",
                    loggedAndIgnored);
        }
        userTransactionManager = null;
    }

    @Override
    public String name() {
        return "Atomikos";
    }

    private synchronized Object ensureUserTransactionManager() {
        Object current = userTransactionManager;
        if (current != null) {
            return current;
        }
        try {
            Class<?> userTransactionManagerClass = forName(ATOMIKOS_USER_TRANSACTION_MANAGER_CLASS);
            Object instance = userTransactionManagerClass.getDeclaredConstructor().newInstance();
            // setForceShutdown(false) leaves recovery threads alone on
            // close — appropriate when Atomikos's own shutdown hook will
            // run after the test JVM exits this strategy. setStartupTransactionService
            // is true by default in 6.x.
            try {
                userTransactionManagerClass.getMethod("setForceShutdown", boolean.class)
                        .invoke(instance, false);
            } catch (NoSuchMethodException olderApi) {
                // Older Atomikos releases lack the setter; the default is fine.
            }
            userTransactionManagerClass.getMethod("init").invoke(instance);
            this.userTransactionManager = instance;
            return instance;
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new IllegalStateException(
                    "Failed to initialise Atomikos UserTransactionManager via reflection",
                    reflectionFailure);
        }
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
