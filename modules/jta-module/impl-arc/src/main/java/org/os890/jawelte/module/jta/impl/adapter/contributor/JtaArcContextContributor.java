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
package org.os890.jawelte.module.jta.impl.adapter.contributor;

import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.os890.jawelte.core.api.port.ConfigResolver;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.impl.spi.ArcContextContributor;
import org.os890.jawelte.module.jpa.api.port.CdiTransactionalSupportProvider;
import org.os890.jawelte.module.jpa.api.port.TransactionStrategy;

import io.quarkus.arc.processor.BeanProcessor;
import io.quarkus.arc.processor.DotNames;

/**
 * jta-module's {@link ArcContextContributor}. Replaces the previous
 * {@code JtaCdiExtension} (a CDI portable extension, unsupported by
 * Quarkus ArC) with the ArC-native equivalents:
 *
 * <ul>
 *   <li>Pre-bootstraps the active {@link TransactionStrategy} (a
 *       no-op for RESOURCE_LOCAL; under JTA the call triggers the
 *       provider's lazy resolution including
 *       {@code JndiArtifactBinder}).</li>
 *   <li>Seeds Narayana's {@code JTAEnvironmentBean} static singleton
 *       with a configured JNDI context name when its CDI integration
 *       is on the classpath.</li>
 *   <li>Adds an {@code addExcludeType} filter that drops Geronimo
 *       internals from the bean archive (configurable allowlist via
 *       the MP Config key
 *       {@code org.os890.jawelte.module.jta.vendor-veto.allowlist.packages})
 *       and — when delegating to a vendor JTA CDI integration —
 *       additionally drops jpa-module's own
 *       {@code TransactionalInterceptor} (so it doesn't double-fire)
 *       and Narayana's {@code JTAEnvironmentBean} (so the synthetic
 *       bean registered below wins outright).</li>
 *   <li>When delegating, registers a synthetic CDI bean for
 *       {@code JTAEnvironmentBean} that returns the
 *       {@code BeanPopulator}'s default instance — the same instance
 *       the static seed above just configured.</li>
 * </ul>
 *
 * <p>Discovered via
 * {@code META-INF/services/org.os890.jawelte.module.cdi.impl.spi.ArcContextContributor}.
 */
public class JtaArcContextContributor implements ArcContextContributor {

    private static final String VENDOR_VETO_ALLOWLIST_KEY =
            "org.os890.jawelte.module.jta.vendor-veto.allowlist.packages";

    private static final String JPA_TRANSACTIONAL_INTERCEPTOR_CLASS_NAME =
            "org.os890.jawelte.module.jpa.impl.adapter.interceptor.TransactionalInterceptor";

    private static final String NARAYANA_JTA_ENV_BEAN_CLASS_NAME =
            "com.arjuna.ats.jta.common.JTAEnvironmentBean";

    private static final String NARAYANA_BEAN_POPULATOR_CLASS_NAME =
            "com.arjuna.common.internal.util.propertyservice.BeanPopulator";

    private static final Set<String> VENDOR_VETO_PACKAGE_PREFIXES = Set.of(
            "org.apache.geronimo.transaction.");

    /**
     * Narayana's CDI integration package. Vetoed when jpa-module hosts
     * the {@code @Transactional} interceptor / {@code @TransactionScoped}
     * context (i.e. when {@code platformProvidesTransactionalInterceptor()}
     * is {@code false}; under ArC the JTA support provider reports
     * exactly this because Narayana's
     * {@code TransactionalInterceptorBase} probes for Weld at runtime
     * and throws on anything else). Narayana's core TM classes
     * ({@code com.arjuna.ats.arjuna.*},
     * {@code com.arjuna.ats.jta.common.*}, etc.) survive — the veto
     * only drops the {@code cdi/*} subpackage so the
     * {@code narayana-jta} uber jar can still serve as the TM provider.
     */
    private static final String NARAYANA_CDI_PACKAGE_PREFIX =
            "com.arjuna.ats.jta.cdi.";

    /** No-arg constructor required by {@code ServiceLoader}. */
    public JtaArcContextContributor() {
    }

    @Override
    public void contribute(TestContext testContext, BeanProcessor.Builder builder) {
        // Pre-bootstrap the active TransactionStrategy. RESOURCE_LOCAL
        // is a no-op (returns null TM); under JTA the call triggers
        // lazy TransactionManagerProvider resolution, including the
        // JndiArtifactBinder that puts the active provider's TM/UT/TSR
        // into JNDI.
        TestContext.loadService(TransactionStrategy.class).getTransactionManager();
        seedNarayanaJtaEnvironmentBeanIfPresent();

        boolean delegating = supportProvider().platformProvidesTransactionalInterceptor();
        Set<String> vendorVetoAllowlist = readVendorVetoAllowlist();

        DotName jpaTxInterceptor = DotName.createSimple(JPA_TRANSACTIONAL_INTERCEPTOR_CLASS_NAME);
        DotName narayanaJtaEnvBean = DotName.createSimple(NARAYANA_JTA_ENV_BEAN_CLASS_NAME);

        builder.addExcludeType(classInfo -> {
            String className = classInfo.name().toString();
            if (matchesAllowlist(className, vendorVetoAllowlist)) {
                return false;
            }
            if (delegating && classInfo.name().equals(jpaTxInterceptor)) {
                return true;
            }
            if (delegating && classInfo.name().equals(narayanaJtaEnvBean)) {
                return true;
            }
            if (!delegating && isDirectlyInNarayanaCdiPackage(className)) {
                // Under ArC, jpa-module's interceptor + context are the
                // active ones; veto Narayana's CDI integration package
                // so its @Transactional interceptor / NarayanaTransactionManager
                // never reach the bean archive in the first place.
                // Scoped to the exact com.arjuna.ats.jta.cdi package
                // (not subpackages like .fake) so user-added stand-in
                // beans for testing the veto behaviour stay resolvable.
                return true;
            }
            return matchesVendorVetoTarget(className);
        });

        if (delegating) {
            registerSyntheticJtaEnvironmentBeanIfNeeded(builder, narayanaJtaEnvBean);
        }
    }

    private static void registerSyntheticJtaEnvironmentBeanIfNeeded(
            BeanProcessor.Builder builder, DotName narayanaJtaEnvBean) {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        Class<?> envBeanClass;
        try {
            envBeanClass = Class.forName(NARAYANA_JTA_ENV_BEAN_CLASS_NAME, true, tccl);
        } catch (ClassNotFoundException notPresent) {
            return;
        }
        builder.addBeanRegistrar(registration -> {
            registration.configure(narayanaJtaEnvBean)
                    .scope(ApplicationScoped.class)
                    .addType(envBeanClass)
                    .addQualifier(AnnotationInstance.builder(DotNames.DEFAULT).build())
                    .alternative(true)
                    .priority(Integer.MAX_VALUE)
                    .creator(JtaEnvironmentBeanCreator.class)
                    .done();
        });
    }

    private static void seedNarayanaJtaEnvironmentBeanIfPresent() {
        if (!supportProvider().platformProvidesTransactionalInterceptor()) {
            return;
        }
        try {
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            Class<?> beanPopulator = Class.forName(
                    NARAYANA_BEAN_POPULATOR_CLASS_NAME, true, tccl);
            Class<?> envBeanClass = Class.forName(
                    NARAYANA_JTA_ENV_BEAN_CLASS_NAME, true, tccl);
            Object envBean = beanPopulator
                    .getMethod("getDefaultInstance", Class.class)
                    .invoke(null, envBeanClass);
            envBeanClass
                    .getMethod("setTransactionManagerJNDIContext", String.class)
                    .invoke(envBean, "java:/TransactionManager");
        } catch (ClassNotFoundException notPresent) {
            // Narayana CDI classes truly absent — nothing to seed.
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new IllegalStateException(
                    "Failed to seed Narayana's JTAEnvironmentBean", reflectionFailure);
        }
    }

    private static boolean isDirectlyInNarayanaCdiPackage(String className) {
        if (!className.startsWith(NARAYANA_CDI_PACKAGE_PREFIX)) {
            return false;
        }
        // The class must be loaded from the narayana-jta jar — user
        // code that happens to live in the same package namespace (test
        // stand-ins like com.arjuna.ats.jta.cdi.fake.FakeNarayanaBean)
        // surfaces from target/test-classes, not from the jar, so we
        // intentionally let those through.
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl == null) {
            tccl = JtaArcContextContributor.class.getClassLoader();
        }
        java.net.URL location = tccl.getResource(className.replace('.', '/') + ".class");
        if (location == null) {
            return false;
        }
        String urlForm = location.toString();
        return urlForm.startsWith("jar:");
    }

    private static boolean matchesVendorVetoTarget(String className) {
        for (String prefix : VENDOR_VETO_PACKAGE_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAllowlist(String className, Set<String> allowlist) {
        for (String prefix : allowlist) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> readVendorVetoAllowlist() {
        return TestContext.loadService(ConfigResolver.class)
                .resolve(VENDOR_VETO_ALLOWLIST_KEY)
                .map(value -> {
                    Set<String> prefixes = new LinkedHashSet<>();
                    for (String entry : value.split(",")) {
                        String trimmed = entry.trim();
                        if (!trimmed.isEmpty()) {
                            prefixes.add(trimmed);
                        }
                    }
                    return prefixes;
                })
                .orElseGet(LinkedHashSet::new);
    }

    private static CdiTransactionalSupportProvider supportProvider() {
        return TestContext.loadService(CdiTransactionalSupportProvider.class);
    }

    /**
     * Synthetic-bean creator that returns the {@code BeanPopulator}
     * default instance of Narayana's {@code JTAEnvironmentBean} —
     * the same instance
     * {@link JtaArcContextContributor#seedNarayanaJtaEnvironmentBeanIfPresent()}
     * just seeded with the correct JNDI context name.
     */
    public static class JtaEnvironmentBeanCreator
            implements io.quarkus.arc.BeanCreator<Object> {

        /** No-arg constructor required by ArC's reflective creator lookup. */
        public JtaEnvironmentBeanCreator() {
        }

        @Override
        public Object create(io.quarkus.arc.SyntheticCreationalContext<Object> context) {
            try {
                ClassLoader tccl = Thread.currentThread().getContextClassLoader();
                Class<?> envBeanClass = Class.forName(NARAYANA_JTA_ENV_BEAN_CLASS_NAME, true, tccl);
                Class<?> beanPopulator = Class.forName(
                        NARAYANA_BEAN_POPULATOR_CLASS_NAME, true, tccl);
                return beanPopulator
                        .getMethod("getDefaultInstance", Class.class)
                        .invoke(null, envBeanClass);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(
                        "Failed to resolve Narayana's JTAEnvironmentBean", failure);
            }
        }
    }
}
