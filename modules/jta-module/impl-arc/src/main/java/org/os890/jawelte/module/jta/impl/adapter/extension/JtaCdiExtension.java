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
package org.os890.jawelte.module.jta.impl.adapter.extension;

import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.enterprise.event.Observes;
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
    /**
     * Force {@code JTAEnvironmentBean} through {@code ProcessAnnotatedType}
     * so our veto observer below can suppress it. Weld 6 auto-discovers
     * the class via {@code Instance<JTAEnvironmentBean>} injection
     * points without ever firing PAT — {@code addAnnotatedType} here
     * forces the type into the discovery pipeline so the veto applies.
     */
    void onBeforeBeanDiscovery(
            @Observes jakarta.enterprise.inject.spi.BeforeBeanDiscovery event,
            jakarta.enterprise.inject.spi.BeanManager beanManager) {
        if (!supportProvider().platformProvidesTransactionalInterceptor()) {
            return;
        }
        try {
            Class<?> envBean = Class.forName(
                    NARAYANA_JTA_ENV_BEAN_CLASS_NAME, false,
                    Thread.currentThread().getContextClassLoader());
            event.addAnnotatedType(beanManager.createAnnotatedType(envBean),
                    "jawelte-jta-module-jtaEnvironmentBean-veto");
        } catch (ClassNotFoundException notPresent) {
            // Narayana's CDI integration not actually on the classpath
            // — support provider's probe is stale, no veto needed.
        }
    }

    <T> void onProcessAnnotatedTypeForVendorVeto(@Observes ProcessAnnotatedType<T> event) {
        String className = event.getAnnotatedType().getJavaClass().getName();
        if (matchesVendorVetoAllowlist(className)) {
            return;
        }
        boolean delegating = supportProvider().platformProvidesTransactionalInterceptor();
        if (delegating && JPA_TRANSACTIONAL_INTERCEPTOR_CLASS_NAME.equals(className)) {
            // jpa-module's own @Transactional interceptor must not
            // double-fire alongside the vendor's (Narayana's). The
            // vendor wins by default — its @Transactional interceptor
            // is enabled via Narayana's beans.xml. We veto ours so
            // both don't run.
            event.veto();
            return;
        }
        if (delegating && NARAYANA_JTA_ENV_BEAN_CLASS_NAME.equals(className)) {
            // Weld's auto-discovery picks JTAEnvironmentBean up as an
            // @ApplicationScoped bean; the instance Weld creates has
            // a null transactionManagerJNDIContext field (the
            // constructor default — "java:/TransactionManager" — is
            // not preserved through Weld's bean creation path) and
            // Narayana's NarayanaTransactionManager.getDelegate hits
            // an NPE in JTASupplier.get when jndiName is null.
            // Vetoing here makes Instance<JTAEnvironmentBean>
            // unsatisfied; Narayana then falls through to its
            // BeanPopulator default, which has the proper default
            // JNDI name and routes the lookup through JNDI to the
            // TM bound by JndiArtifactBinder. OWB doesn't auto-discover
            // it (no bean-defining annotation), so this veto is a
            // no-op there.
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
        // lazy TransactionManagerProvider resolution, including the
        // JndiArtifactBinder that puts the active provider's TM/UT/TSR
        // into JNDI.
        TestContext.loadService(TransactionStrategy.class).getTransactionManager();
        seedNarayanaJtaEnvironmentBeanIfPresent();
        registerSyntheticJtaEnvironmentBeanIfNeeded(event);
    }

    /**
     * Register a synthetic CDI {@code JTAEnvironmentBean} bean that
     * returns the {@code BeanPopulator} default — the same instance
     * {@link #seedNarayanaJtaEnvironmentBeanIfPresent()} seeded with
     * the correct JNDI context name. Marked as an
     * {@code @Alternative} with high {@code @Priority} so it wins
     * over Weld 6's auto-discovered {@code JTAEnvironmentBean} (which
     * gets created without the constructor's field initialisers
     * running and leaves {@code transactionManagerJNDIContext} null,
     * tripping {@code JTASupplier.get}'s {@code requireNonNull}).
     *
     * <p>Reflection-only; no compile-time dependency on Narayana.
     */
    private static void registerSyntheticJtaEnvironmentBeanIfNeeded(AfterBeanDiscovery event) {
        if (!supportProvider().platformProvidesTransactionalInterceptor()) {
            return;
        }
        try {
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            Class<?> envBeanClass = Class.forName(
                    NARAYANA_JTA_ENV_BEAN_CLASS_NAME, true, tccl);
            Class<?> beanPopulator = Class.forName(
                    NARAYANA_BEAN_POPULATOR_CLASS_NAME, true, tccl);
            Object envBean = beanPopulator
                    .getMethod("getDefaultInstance", Class.class)
                    .invoke(null, envBeanClass);
            event.<Object>addBean()
                    .beanClass(envBeanClass)
                    .types(envBeanClass, Object.class)
                    .qualifiers(jakarta.enterprise.inject.Default.Literal.INSTANCE,
                            jakarta.enterprise.inject.Any.Literal.INSTANCE)
                    .scope(jakarta.enterprise.context.ApplicationScoped.class)
                    .alternative(true)
                    .priority(Integer.MAX_VALUE)
                    .produceWith(instance -> envBean);
        } catch (ClassNotFoundException notPresent) {
            // Narayana CDI classes absent; nothing to register.
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new IllegalStateException(
                    "Failed to register synthetic JTAEnvironmentBean", reflectionFailure);
        }
    }

    /**
     * Ensure the static {@code JTAEnvironmentBean} singleton
     * {@code BeanPopulator} caches has its
     * {@code transactionManagerJNDIContext} field set to
     * {@code "java:/TransactionManager"}. The class's no-arg
     * constructor sets this default, but some CDI runtime paths
     * (Weld 6 in particular) construct {@code JTAEnvironmentBean}
     * without running the constructor's field initialisers — the
     * field stays {@code null} and {@code JTASupplier.get}'s
     * {@code requireNonNull(jndiName)} throws NPE.
     *
     * <p>Independent of which {@code TransactionManagerProvider} is
     * active — the field needs seeding whenever Narayana's CDI
     * integration is on the classpath, even if the active TM is
     * Geronimo (because {@code NarayanaTransactionManager} still
     * gets constructed by CDI and reads the field).
     */
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
