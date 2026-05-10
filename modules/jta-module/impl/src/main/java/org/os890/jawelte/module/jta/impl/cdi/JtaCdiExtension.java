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
package org.os890.jawelte.module.jta.impl.cdi;

import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;

import org.os890.jawelte.core.api.port.ConfigResolver;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.port.CdiTransactionalSupportProvider;
import org.os890.jawelte.module.jpa.api.port.TransactionStrategy;

/**
 * CDI Extension shipped by {@code jta-module/impl}. Hosts the
 * JTA-vendor-specific CDI plumbing that used to live in
 * {@code jpa-module/impl/JpaCdiExtension} — the architectural
 * dependency now flows the right way: {@code jta-module} depends on
 * {@code jpa-module}, never the reverse.
 *
 * <ul>
 *   <li><strong>Vendor-veto observer</strong> — vetoes types in
 *       {@code org.apache.geronimo.transaction.*} (Geronimo doesn't
 *       ship a CDI integration we delegate to, so any CDI beans that
 *       sneak in transitively are kept out of the bean set).
 *       Allowlist via the
 *       {@code org.os890.jawelte.module.jta.vendor-veto.allowlist.packages}
 *       MP Config key for downstream modules that legitimately want
 *       certain prefixes registered.</li>
 *   <li><strong>Delegation veto branches</strong> — when
 *       {@link CdiTransactionalSupportProvider#platformProvidesTransactionalInterceptor()}
 *       reports {@code true} (Narayana's CDI integration is on the
 *       classpath), additionally vetoes
 *       {@code org.os890.jawelte.module.jpa.impl.adapter.interceptor.TransactionalInterceptor}
 *       so it doesn't double-fire alongside the vendor's interceptor,
 *       and vetoes Narayana's {@code JTAEnvironmentBean} so Weld's
 *       implicit-discovery doesn't compete with the synthetic bean
 *       this Extension registers in {@code AfterBeanDiscovery}.</li>
 *   <li><strong>AfterBeanDiscovery</strong> — pre-bootstraps the
 *       active {@code TransactionStrategy} (no-op for RESOURCE_LOCAL;
 *       under JTA the call triggers lazy
 *       {@code TransactionManagerProvider} resolution which seeds
 *       Narayana's {@code JTAEnvironmentBean} static singleton with
 *       a configured {@code TransactionManager}). When delegating,
 *       also registers a synthetic CDI bean for that singleton so
 *       Narayana's {@code NarayanaTransactionManager} bean
 *       construction sees a configured TM regardless of CDI runtime
 *       (see the in-method comments for the Weld-specific reason).</li>
 * </ul>
 */
public class JtaCdiExtension implements Extension {

    /**
     * MicroProfile Config key whose value (comma-separated) lists
     * package prefixes that are <em>exempt</em> from the
     * vendor-internal CDI-bean vetoing observer below. Set when a
     * downstream module legitimately ships beans in
     * {@code org.apache.geronimo.transaction.*} that the user wants
     * registered.
     */
    private static final String VENDOR_VETO_ALLOWLIST_KEY =
            "org.os890.jawelte.module.jta.vendor-veto.allowlist.packages";

    /**
     * jpa-module's {@code @Transactional} interceptor — vetoed at PAT
     * when delegating to a vendor JTA CDI integration so the vendor's
     * interceptor wins outright (no double-interception). String
     * literal to avoid pulling jpa-module/impl onto this Extension's
     * compile-time API surface.
     */
    private static final String JPA_TRANSACTIONAL_INTERCEPTOR_CLASS_NAME =
            "org.os890.jawelte.module.jpa.impl.adapter.interceptor.TransactionalInterceptor";

    /**
     * Narayana's {@code JTAEnvironmentBean} — vetoed at PAT when
     * delegating so Weld's implicit-discovery doesn't register it as
     * a CDI bean alongside the synthetic one this Extension adds.
     * Without the veto the two collide as
     * {@code AmbiguousResolutionException} when Narayana's
     * {@code NarayanaTransactionManager} resolves
     * {@code Instance<JTAEnvironmentBean>}. OWB doesn't emit a bean
     * for it (the class carries no bean-defining annotation), so this
     * veto is a no-op there.
     */
    private static final String NARAYANA_JTA_ENV_BEAN_CLASS_NAME =
            "com.arjuna.ats.jta.common.JTAEnvironmentBean";

    /** Narayana's static {@code BeanPopulator} cache. */
    private static final String NARAYANA_BEAN_POPULATOR_CLASS_NAME =
            "com.arjuna.common.internal.util.propertyservice.BeanPopulator";

    /**
     * Package prefixes whose CDI beans are vetoed at PAT to avoid
     * duplicate-bean conflicts. Geronimo doesn't ship a CDI integration
     * we want to delegate to, so its CDI beans (if any sneak in via
     * transitives) stay vetoed. Narayana's {@code com.arjuna.ats.jta.cdi.*}
     * beans are kept — when Narayana's CDI integration is on the
     * classpath we delegate to it via the
     * {@link CdiTransactionalSupportProvider} seam.
     */
    private static final Set<String> VENDOR_VETO_PACKAGE_PREFIXES = Set.of(
            "org.apache.geronimo.transaction.");

    private volatile Set<String> vendorVetoAllowlist;

    /** No-arg constructor required by the CDI runtime. */
    public JtaCdiExtension() {
    }

    /**
     * Vetoes vendor-internal types and (when delegating) jpa-module's
     * own {@code TransactionalInterceptor} plus Narayana's
     * {@code JTAEnvironmentBean} so the synthetic bean we register in
     * {@code AfterBeanDiscovery} is the only one Weld's
     * {@code Instance<JTAEnvironmentBean>} resolves.
     *
     * @param event the {@code ProcessAnnotatedType} event
     * @param <T>   the annotated type's class type parameter
     */
    <T> void onProcessAnnotatedTypeForVendorVeto(@Observes ProcessAnnotatedType<T> event) {
        String className = event.getAnnotatedType().getJavaClass().getName();
        if (matchesVendorVetoAllowlist(className)) {
            return;
        }
        boolean delegating = supportProvider().platformProvidesTransactionalInterceptor();
        if (delegating
                && (JPA_TRANSACTIONAL_INTERCEPTOR_CLASS_NAME.equals(className)
                        || NARAYANA_JTA_ENV_BEAN_CLASS_NAME.equals(className))) {
            event.veto();
            return;
        }
        if (matchesVendorVetoTarget(className)) {
            event.veto();
        }
    }

    void onAfterBeanDiscovery(@Observes AfterBeanDiscovery event) {
        // Pre-bootstrap the active TransactionStrategy. RESOURCE_LOCAL
        // is a no-op (returns null TM); under JTA the call triggers
        // lazy TransactionManagerProvider resolution. Narayana's
        // provider pre-seeds JTAEnvironmentBean from create() so its
        // CDI bean (which constructs lazily on first @Transactional
        // fire and looks up the same JTAEnvironmentBean) sees a
        // configured TM regardless of when the application first
        // dereferences it.
        TestContext.loadService(TransactionStrategy.class).getTransactionManager();
        registerSyntheticVendorJtaEnvironmentBeanIfNeeded(event);
    }

    /**
     * Register a synthetic CDI bean for Narayana's
     * {@code JTAEnvironmentBean} that produces the static singleton
     * {@code BeanPopulator} caches (which jta-module's
     * {@code NarayanaTransactionManagerProvider.create()} pre-seeds
     * with a configured {@code TransactionManager}).
     *
     * <p>Without this synthetic bean, Weld's implicit-discovery path
     * lets Narayana's {@code NarayanaTransactionManager} see an
     * {@code Instance<JTAEnvironmentBean>} satisfied by a fresh
     * Weld-managed instance whose state diverges from the seeded
     * singleton — its {@code transactionManager} is {@code null} and
     * the constructor NPEs deep inside {@code JTASupplier.get(...)}.
     * OWB doesn't hit this because its instance lookup falls through
     * to the unsatisfied path and reads the same singleton we seed.
     *
     * <p>Reflection-only — jta-module/impl never compile-depends on
     * Narayana. No-op when Narayana isn't on the classpath.
     */
    private static void registerSyntheticVendorJtaEnvironmentBeanIfNeeded(AfterBeanDiscovery event) {
        if (!supportProvider().platformProvidesTransactionalInterceptor()) {
            return;
        }
        try {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            Class<?> envBeanClass = Class.forName(
                    NARAYANA_JTA_ENV_BEAN_CLASS_NAME, false, contextClassLoader);
            Class<?> beanPopulatorClass = Class.forName(
                    NARAYANA_BEAN_POPULATOR_CLASS_NAME, false, contextClassLoader);
            Object seededSingleton = beanPopulatorClass
                    .getMethod("getDefaultInstance", Class.class)
                    .invoke(null, envBeanClass);
            event.<Object>addBean()
                    .beanClass(envBeanClass)
                    .types(envBeanClass, Object.class)
                    .qualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE)
                    .scope(ApplicationScoped.class)
                    .produceWith(instance -> seededSingleton);
        } catch (ClassNotFoundException notPresent) {
            // Narayana classes truly absent — should not happen given
            // platformProvidesTransactionalInterceptor() returned true,
            // but tolerate the race rather than throw.
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new IllegalStateException(
                    "Failed to register synthetic JTAEnvironmentBean for Narayana CDI bootstrap",
                    reflectionFailure);
        }
    }

    private static boolean matchesVendorVetoTarget(String className) {
        for (String prefix : VENDOR_VETO_PACKAGE_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesVendorVetoAllowlist(String className) {
        Set<String> allowlist = vendorVetoAllowlist;
        if (allowlist == null) {
            synchronized (this) {
                if (vendorVetoAllowlist == null) {
                    vendorVetoAllowlist = readVendorVetoAllowlist();
                }
                allowlist = vendorVetoAllowlist;
            }
        }
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
}
