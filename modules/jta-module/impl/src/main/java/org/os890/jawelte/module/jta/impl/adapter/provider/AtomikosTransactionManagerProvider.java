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
package org.os890.jawelte.module.jta.impl.adapter.provider;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;
import javax.sql.XADataSource;

import jakarta.annotation.Priority;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.UserTransaction;

import org.os890.jawelte.module.jta.api.port.TransactionManagerProvider;
import org.os890.jawelte.module.jta.impl.config.JtaConfig;

/**
 * Default {@link TransactionManagerProvider} for the Atomikos
 * TransactionsEssentials JTA implementation
 * ({@code com.atomikos:transactions-jta}).
 *
 * <p>Not pre-registered in jta-module's
 * {@code META-INF/services/...TransactionManagerProvider} — the
 * default that ships there is the
 * {@link AutoSelectTransactionManagerProvider} wrapper, which probes
 * the classpath and delegates here when Atomikos's JTA classes are
 * loadable. Consumers force this provider directly by shipping their
 * own {@code META-INF/services} file naming this class; the
 * {@code @Priority(Integer.MAX_VALUE - 101)} below is lower than the
 * wrapper's {@code @Priority(Integer.MAX_VALUE)} so the explicit
 * registration wins.
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
@Priority(Integer.MAX_VALUE - 101)
public class AtomikosTransactionManagerProvider implements TransactionManagerProvider {

    private static final Logger LOG =
            System.getLogger(AtomikosTransactionManagerProvider.class.getName());

    private static final String ATOMIKOS_USER_TRANSACTION_MANAGER_CLASS =
            "com.atomikos.icatch.jta.UserTransactionManager";

    /**
     * Atomikos reads this system property during
     * {@code UserTransactionManager.init()} and uses it as the JVM-wide
     * default transaction timeout. The value is expressed in
     * <em>milliseconds</em>, so jta-module converts the
     * {@link JtaConfig#defaultTransactionTimeoutSeconds()} value at the
     * boundary.
     */
    private static final String ATOMIKOS_DEFAULT_JTA_TIMEOUT_MS_PROPERTY =
            "com.atomikos.icatch.default_jta_timeout";

    /**
     * Disable Atomikos's recovery log for the test stack. The
     * default writes {@code tmlog<n>.log} into the JVM working
     * directory which leaks state across test runs and trips Apache
     * RAT checks; the in-memory H2 test scenarios do not need
     * crash-recovery so the log is pure overhead. Production users
     * who bring their own Atomikos build override this via a system
     * property — we only seed the default when nothing is set.
     */
    private static final String ATOMIKOS_ENABLE_LOGGING_PROPERTY =
            "com.atomikos.icatch.enable_logging";

    /**
     * Atomikos's pooled {@link DataSource} bridge around a raw
     * {@link XADataSource}. Owns the XAConnection pool, integrates
     * with Atomikos's resource manager + recovery subsystem, and
     * handles {@code Transaction.enlistResource(...)} internally —
     * which is the only way to satisfy Atomikos's strict
     * "registered-resource recoverable" enlistment check when H2 is
     * the underlying database (H2's {@code XAResource.isSameRM} is
     * identity-only, so the project's regular
     * {@code XaDataSourceWrapper} approach of opening fresh
     * {@code XAConnection}s and enlisting their {@code XAResource}s
     * directly does not match anything Atomikos has registered).
     */
    private static final String ATOMIKOS_DATA_SOURCE_BEAN_CLASS =
            "com.atomikos.jdbc.AtomikosDataSourceBean";

    /**
     * JVM-static cache. {@link ServiceLoader} returns a fresh provider
     * instance per {@code TestContext.loadService(...)} call, so an
     * instance-level cache would result in multiple
     * {@code UserTransactionManager} instances — each driving its own
     * {@code TransactionManager} — leading to cross-instance tx
     * mismatches between the strategy's {@code begin()} and the
     * JtaPlatform's {@code locateTransactionManager()}.
     */
    private static volatile Object cachedUserTransactionManager;

    private static final Object LOCK = new Object();

    /**
     * Cache of pooled DataSources keyed by persistence unit name.
     * One {@code AtomikosDataSourceBean} per PU; constructed lazily
     * during {@link #pooledJtaDataSource(XADataSource, String)} and
     * closed in {@link #shutdown()}.
     */
    private static final Map<String, Object> POOLED_DATA_SOURCES_BY_PU =
            new ConcurrentHashMap<>();

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
    public TransactionSynchronizationRegistry transactionSynchronizationRegistry() {
        try {
            Class<?> tsrClass = Class.forName(
                    "com.atomikos.icatch.jta.TransactionSynchronizationRegistryImp",
                    true, Thread.currentThread().getContextClassLoader());
            return (TransactionSynchronizationRegistry)
                    tsrClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new IllegalStateException(
                    "Failed to instantiate Atomikos TransactionSynchronizationRegistry via reflection",
                    reflectionFailure);
        }
    }

    @Override
    public Optional<DataSource> pooledJtaDataSource(
            XADataSource dataSource, String persistenceUnitName) {
        // Ensure the TM is up first: AtomikosDataSourceBean.init()
        // registers the bean with the same TransactionService that
        // UserTransactionManager.init() bootstraps.
        ensureUserTransactionManager();
        Object cached = POOLED_DATA_SOURCES_BY_PU.get(persistenceUnitName);
        if (cached != null) {
            return Optional.of((DataSource) cached);
        }
        synchronized (LOCK) {
            cached = POOLED_DATA_SOURCES_BY_PU.get(persistenceUnitName);
            if (cached != null) {
                return Optional.of((DataSource) cached);
            }
            try {
                Class<?> beanClass = forName(ATOMIKOS_DATA_SOURCE_BEAN_CLASS);
                Object bean = beanClass.getDeclaredConstructor().newInstance();
                beanClass.getMethod("setUniqueResourceName", String.class)
                        .invoke(bean, persistenceUnitName);
                beanClass.getMethod("setXaDataSource", XADataSource.class)
                        .invoke(bean, dataSource);
                // Pool size 1 is enough for our single-threaded
                // test scenarios; tune higher for production.
                beanClass.getMethod("setMaxPoolSize", int.class).invoke(bean, 5);
                beanClass.getMethod("setMinPoolSize", int.class).invoke(bean, 1);
                beanClass.getMethod("init").invoke(bean);
                POOLED_DATA_SOURCES_BY_PU.put(persistenceUnitName, bean);
                return Optional.of((DataSource) bean);
            } catch (ReflectiveOperationException reflectionFailure) {
                throw new IllegalStateException(
                        "Failed to construct Atomikos AtomikosDataSourceBean for persistence unit '"
                                + persistenceUnitName + "' via reflection",
                        reflectionFailure);
            }
        }
    }

    @Override
    public void shutdown() {
        // Close pooled DataSourceBeans before the TM goes down —
        // each bean unregisters itself with the resource manager on
        // close() so the TM's own shutdown sees an empty registry.
        for (Map.Entry<String, Object> entry : POOLED_DATA_SOURCES_BY_PU.entrySet()) {
            try {
                entry.getValue().getClass().getMethod("close").invoke(entry.getValue());
            } catch (ReflectiveOperationException loggedAndIgnored) {
                LOG.log(Level.WARNING,
                        "Atomikos AtomikosDataSourceBean.close() failed for '"
                                + entry.getKey() + "' via reflection",
                        loggedAndIgnored);
            }
        }
        POOLED_DATA_SOURCES_BY_PU.clear();

        Object current;
        synchronized (LOCK) {
            current = cachedUserTransactionManager;
            cachedUserTransactionManager = null;
        }
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
    }

    @Override
    public String name() {
        return "Atomikos";
    }

    private Object ensureUserTransactionManager() {
        Object local = cachedUserTransactionManager;
        if (local != null) {
            return local;
        }
        synchronized (LOCK) {
            if (cachedUserTransactionManager != null) {
                return cachedUserTransactionManager;
            }
            try {
                // Seed the system properties *before* init() —
                // Atomikos reads them during initialization, not at
                // begin() time. Don't overwrite explicit user-supplied
                // values.
                if (System.getProperty(ATOMIKOS_DEFAULT_JTA_TIMEOUT_MS_PROPERTY) == null) {
                    long defaultTimeoutMillis =
                            new JtaConfig().defaultTransactionTimeoutSeconds() * 1000L;
                    System.setProperty(
                            ATOMIKOS_DEFAULT_JTA_TIMEOUT_MS_PROPERTY,
                            Long.toString(defaultTimeoutMillis));
                }
                if (System.getProperty(ATOMIKOS_ENABLE_LOGGING_PROPERTY) == null) {
                    System.setProperty(ATOMIKOS_ENABLE_LOGGING_PROPERTY, "false");
                }
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
                cachedUserTransactionManager = instance;
                return instance;
            } catch (ReflectiveOperationException reflectionFailure) {
                throw new IllegalStateException(
                        "Failed to initialise Atomikos UserTransactionManager via reflection",
                        reflectionFailure);
            }
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
