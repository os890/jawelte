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
import jakarta.transaction.TransactionSynchronizationRegistry;
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
 * <p>Note: jpa-module's {@code JpaCdiExtension} delegates the CDI
 * {@code @Transactional} interceptor + {@code @TransactionScoped}
 * context to Narayana's bundled extension when its
 * {@code TransactionExtension} is on the classpath. The TM itself is
 * reached via the {@code com.arjuna.ats.jta.TransactionManager} static
 * accessor — it is not a CDI bean.
 *
 * <p>Pre-seeds {@code JTAEnvironmentBean.transactionManager} on
 * {@link #create()} via reflection. Without that seeding, Narayana's
 * {@code NarayanaTransactionManager} CDI bean's constructor calls
 * {@code JTASupplier.get(...)} which (via the fallback supplier) reads
 * {@code BeanPopulator}'s default {@code JTAEnvironmentBean} —
 * unconfigured, that returns {@code null} and the NPE bubbles up as a
 * Weld bean-creation failure. OWB's bootstrap order happens to land
 * after Narayana's static {@code TransactionManager.transactionManager()}
 * accessor has already populated the bean, so the seeding is a no-op
 * there.
 */
@Priority(Integer.MAX_VALUE)
public class NarayanaTransactionManagerProvider implements TransactionManagerProvider {

    private static final String NARAYANA_TM_ACCESSOR_CLASS =
            "com.arjuna.ats.jta.TransactionManager";

    private static final String NARAYANA_USER_TRANSACTION_ACCESSOR_CLASS =
            "com.arjuna.ats.jta.UserTransaction";

    private static final String NARAYANA_BEAN_POPULATOR_CLASS =
            "com.arjuna.common.internal.util.propertyservice.BeanPopulator";

    private static final String NARAYANA_JTA_ENV_BEAN_CLASS =
            "com.arjuna.ats.jta.common.JTAEnvironmentBean";

    private static final String NARAYANA_CORE_ENV_BEAN_CLASS =
            "com.arjuna.ats.arjuna.common.CoreEnvironmentBean";

    private static final String NARAYANA_TSR_IMPLE_CLASS =
            "com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple";

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
            // Pre-seed CoreEnvironmentBean.nodeIdentifier before any
            // tx is begun: 2-PC commit fails with "ARJUNA016111: The
            // node identifier cannot be null" otherwise. The uber
            // narayana-jta jar bundles a jbossts-properties.xml that
            // BeanPopulator reads on first JTAEnvironmentBean access;
            // the lean jta jar doesn't bundle it, leaving the field
            // null until something configures it.
            seedCoreEnvironmentBean();
            Class<?> accessor = forName(NARAYANA_TM_ACCESSOR_CLASS);
            TransactionManager tm = (TransactionManager) accessor.getMethod("transactionManager").invoke(null);
            seedJtaEnvironmentBean(tm);
            return tm;
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new IllegalStateException(
                    "Failed to obtain Narayana TransactionManager via reflection",
                    reflectionFailure);
        }
    }

    private static void seedCoreEnvironmentBean() {
        try {
            Class<?> beanPopulator = forName(NARAYANA_BEAN_POPULATOR_CLASS);
            Class<?> coreEnvBeanClass = forName(NARAYANA_CORE_ENV_BEAN_CLASS);
            Object envBean = beanPopulator
                    .getMethod("getDefaultInstance", Class.class)
                    .invoke(null, coreEnvBeanClass);
            String currentNodeIdentifier = (String) coreEnvBeanClass
                    .getMethod("getNodeIdentifier")
                    .invoke(envBean);
            if (currentNodeIdentifier == null || currentNodeIdentifier.isEmpty()) {
                coreEnvBeanClass
                        .getMethod("setNodeIdentifier", String.class)
                        .invoke(envBean, "1");
            }
        } catch (ReflectiveOperationException notSeedable) {
            // best-effort
        }
    }

    /**
     * Set the resolved TM on the static {@code JTAEnvironmentBean}
     * default singleton so Narayana's CDI {@code NarayanaTransactionManager}
     * bean's constructor (which queries the same singleton on first
     * dereference) sees a non-null TM regardless of CDI runtime
     * bootstrap order. Best-effort: silently no-ops if either Narayana's
     * BeanPopulator or its JTAEnvironmentBean class can't be found —
     * that means Narayana isn't on the classpath in the way we expect,
     * and the original NPE will surface from the unmodified code path
     * rather than being masked by a reflection failure here.
     */
    private static void seedJtaEnvironmentBean(TransactionManager tm) {
        try {
            Class<?> beanPopulator = forName(NARAYANA_BEAN_POPULATOR_CLASS);
            Class<?> envBeanClass = forName(NARAYANA_JTA_ENV_BEAN_CLASS);
            Object envBean = beanPopulator
                    .getMethod("getDefaultInstance", Class.class)
                    .invoke(null, envBeanClass);
            envBeanClass
                    .getMethod("setTransactionManager", TransactionManager.class)
                    .invoke(envBean, tm);
        } catch (ReflectiveOperationException notSeedable) {
            // best-effort
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
    public TransactionSynchronizationRegistry transactionSynchronizationRegistry() {
        try {
            Class<?> tsrClass = forName(NARAYANA_TSR_IMPLE_CLASS);
            return (TransactionSynchronizationRegistry)
                    tsrClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new IllegalStateException(
                    "Failed to instantiate Narayana TransactionSynchronizationRegistryImple via reflection",
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
