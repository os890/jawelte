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
package org.os890.jawelte.module.jpa.impl.adapter.extension;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.literal.InjectLiteral;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessProducerField;
import jakarta.enterprise.inject.spi.ProcessProducerMethod;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.enterprise.inject.spi.configurator.AnnotatedFieldConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceUnit;
import jakarta.transaction.Transactional;
import jakarta.transaction.UserTransaction;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;
import org.os890.jawelte.module.jpa.api.ReadOnly;
import org.os890.jawelte.module.jpa.api.port.PersistencePropertyResolver;
import org.os890.jawelte.module.jpa.impl.adapter.context.TransactionScopedContext;
import org.os890.jawelte.module.jpa.impl.adapter.tx.UserTransactionImpl;
import org.os890.jawelte.module.jpa.impl.util.EmfCache;
import org.os890.jawelte.module.jpa.impl.util.EntityManagerProxy;
import org.os890.jawelte.module.jpa.impl.util.EntityScanner;
import org.os890.jawelte.module.jpa.impl.util.JpaActivePersistenceUnits;
import org.os890.jawelte.module.jpa.impl.util.PersistenceXmlParser;
import org.os890.jawelte.module.jpa.impl.util.PersistenceXmlParser.ParsedPersistenceUnit;

/**
 * CDI Extension shipped by jpa-module. Wires the JPA test plumbing
 * into the active {@code SeContainer}:
 *
 * <ul>
 *   <li><strong>{@code BeforeBeanDiscovery}</strong>: read
 *       {@link PersistenceConfig} from the active test class, parse
 *       every reachable {@code persistence.xml}, filter the
 *       persistence-unit set per the annotation, run ASM-based
 *       entity discovery for persistence units with no
 *       {@code <class>} elements, pre-warm one
 *       {@link EntityManagerFactory} per active persistence unit in
 *       {@link EmfCache}, register the interceptor bindings for
 *       {@link Transactional} and {@link ReadOnly}, and seed
 *       {@link JpaActivePersistenceUnits}.</li>
 *   <li><strong>{@code ProcessAnnotatedType}</strong>: rewrite every
 *       {@link PersistenceContext} / {@link PersistenceUnit} field
 *       to {@link jakarta.inject.Inject} (plus
 *       {@link Named} when the {@code unitName} attribute is
 *       non-empty) so JPA-style injection works alongside
 *       CDI-style.</li>
 *   <li><strong>{@code ProcessProducerMethod} /
 *       {@code ProcessProducerField}</strong> for
 *       {@code EntityManagerFactory} / {@code EntityManager}:
 *       record per-persistence-unit user producers so
 *       {@code AfterBeanDiscovery} can back off from registering its
 *       synthetic bean for that persistence unit.</li>
 *   <li><strong>{@code AfterBeanDiscovery}</strong>: register
 *       synthetic beans for {@link EntityManagerFactory} and
 *       {@link EntityManager} per active persistence unit (subject
 *       to user-producer back-off), register a synthetic
 *       {@link UserTransaction} bean, and call
 *       {@link AfterBeanDiscovery#addContext(jakarta.enterprise.context.spi.Context)}
 *       with a fresh {@link TransactionScopedContext}.</li>
 * </ul>
 *
 * <p>If no {@link TestContext} is active on the current thread, the
 * Extension becomes a no-op: the user owns the JPA bootstrap in
 * that case (e.g. {@code @EnableTestBeans(manageContainer=false)}).
 *
 * <p>Re-instantiated per {@code SeContainer}, so the per-instance
 * state below (active persistence units, recorded user producers)
 * is per-test-class.
 */
public class JpaCdiExtension implements Extension {

    private static final String PERSISTENCE_PROPERTY_PREFIX = "org.os890.jawelte.module.jpa.persistence-property.";

    private static final String APP_LABEL_KEY = "org.os890.jawelte.module.jpa.app-label";

    private static final String PROTECTED_PACKAGES_KEY =
            "org.os890.jawelte.module.jpa.api.PersistenceConfig.protected-packages";

    /**
     * MicroProfile Config key whose value (comma-separated) lists
     * package prefixes that are <em>exempt</em> from the
     * vendor-internal CDI-bean vetoing observer. Keep set when a
     * downstream module legitimately ships beans in
     * {@code com.arjuna.ats.jta.cdi.*} or
     * {@code org.apache.geronimo.transaction.*} that the user wants
     * registered.
     */
    private static final String VENDOR_VETO_ALLOWLIST_KEY =
            "org.os890.jawelte.module.jpa.vendor-veto.allowlist.packages";

    /**
     * Package prefixes whose CDI beans are vetoed at PAT to avoid
     * duplicate-bean conflicts when JTA implementations land on the
     * test classpath even though jawelte itself ships only
     * RESOURCE_LOCAL. Defensive measure; an allowlist via
     * {@link #VENDOR_VETO_ALLOWLIST_KEY} exempts specific packages
     * when a downstream module actually wants them registered.
     */
    private static final Set<String> VENDOR_VETO_PACKAGE_PREFIXES = Set.of(
            "com.arjuna.ats.jta.cdi.",
            "org.apache.geronimo.transaction.");

    private boolean active;

    private volatile Set<String> vendorVetoAllowlist;

    private Set<String> activePersistenceUnits = new LinkedHashSet<>();

    private final Map<String, Map<String, Object>> persistenceUnitProperties = new LinkedHashMap<>();

    private final Set<String> userProducedFactoryQualifiers = new HashSet<>();

    private final Set<String> userProducedManagerQualifiers = new HashSet<>();

    /** No-arg constructor required by the CDI runtime. */
    public JpaCdiExtension() {
    }

    void onBeforeBeanDiscovery(@Observes BeforeBeanDiscovery event) {
        TestContext testContext = activeContextOrNull();
        if (testContext == null) {
            return;
        }
        active = true;
        Class<?> testClass = testContext.getTestClass();
        PersistenceConfig persistenceConfig = testClass.getAnnotation(PersistenceConfig.class);

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        List<ParsedPersistenceUnit> parsed = PersistenceXmlParser.parseAll(classLoader);
        Set<String> filter = filterFromAnnotation(persistenceConfig);

        Set<String> resolvedActivePersistenceUnits = new LinkedHashSet<>();
        for (ParsedPersistenceUnit unit : parsed) {
            if (!filter.isEmpty() && !filter.contains(unit.name())) {
                continue;
            }
            resolvedActivePersistenceUnits.add(unit.name());
            Map<String, Object> properties = computeProperties(unit, persistenceConfig, testClass, classLoader);
            persistenceUnitProperties.put(unit.name(), properties);
            EmfCache.getOrCreate(unit.name(), properties);
        }
        activePersistenceUnits = resolvedActivePersistenceUnits;
        JpaActivePersistenceUnits.set(activePersistenceUnits);

        event.addInterceptorBinding(Transactional.class);
        event.addInterceptorBinding(ReadOnly.class);
    }

    <T> void onProcessAnnotatedType(
            @Observes @WithAnnotations({PersistenceContext.class, PersistenceUnit.class})
            ProcessAnnotatedType<T> event) {
        if (!active) {
            return;
        }
        AnnotatedTypeConfigurator<T> configurator = event.configureAnnotatedType();
        for (AnnotatedFieldConfigurator<? super T> field : configurator.fields()) {
            AnnotatedField<? super T> annotated = field.getAnnotated();
            PersistenceContext persistenceContext = annotated.getAnnotation(PersistenceContext.class);
            PersistenceUnit persistenceUnit = annotated.getAnnotation(PersistenceUnit.class);
            if (persistenceContext != null) {
                rewriteField(field, persistenceContext.unitName(), PersistenceContext.class);
            }
            if (persistenceUnit != null) {
                rewriteField(field, persistenceUnit.unitName(), PersistenceUnit.class);
            }
        }
    }

    /**
     * Vetoes types whose package matches one of the
     * vendor-internal prefixes (Narayana / Geronimo JTA CDI beans)
     * unless the user has allowlisted that prefix via
     * {@link #VENDOR_VETO_ALLOWLIST_KEY}. Defensive against
     * duplicate-bean conflicts when a JTA implementation lands on
     * the test classpath even though jawelte itself ships only
     * RESOURCE_LOCAL.
     *
     * @param event the {@code ProcessAnnotatedType} event
     * @param <T>   the annotated type's class type parameter
     */
    <T> void onProcessAnnotatedTypeForVendorVeto(@Observes ProcessAnnotatedType<T> event) {
        if (!active) {
            return;
        }
        String className = event.getAnnotatedType().getJavaClass().getName();
        if (!matchesVendorVetoTarget(className)) {
            return;
        }
        if (matchesVendorVetoAllowlist(className)) {
            return;
        }
        event.veto();
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
        Config config = ConfigProvider.getConfig();
        return config.getOptionalValue(VENDOR_VETO_ALLOWLIST_KEY, String.class)
                .or(() -> config.getOptionalValue(VENDOR_VETO_ALLOWLIST_KEY.replace('.', '_'), String.class))
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
                .orElseGet(Collections::emptySet);
    }

    <X> void onProcessFactoryProducerMethod(
            @Observes ProcessProducerMethod<EntityManagerFactory, X> event) {
        if (!active) {
            return;
        }
        userProducedFactoryQualifiers.add(producerNameKey(event.getBean().getQualifiers()));
    }

    <X> void onProcessFactoryProducerField(
            @Observes ProcessProducerField<EntityManagerFactory, X> event) {
        if (!active) {
            return;
        }
        userProducedFactoryQualifiers.add(producerNameKey(event.getBean().getQualifiers()));
    }

    <X> void onProcessManagerProducerMethod(
            @Observes ProcessProducerMethod<EntityManager, X> event) {
        if (!active) {
            return;
        }
        userProducedManagerQualifiers.add(producerNameKey(event.getBean().getQualifiers()));
    }

    <X> void onProcessManagerProducerField(
            @Observes ProcessProducerField<EntityManager, X> event) {
        if (!active) {
            return;
        }
        userProducedManagerQualifiers.add(producerNameKey(event.getBean().getQualifiers()));
    }

    void onAfterBeanDiscovery(@Observes AfterBeanDiscovery event) {
        if (!active) {
            return;
        }
        boolean singlePersistenceUnit = activePersistenceUnits.size() == 1;
        for (String persistenceUnitName : activePersistenceUnits) {
            EntityManagerFactory factory = EmfCache.getCached(persistenceUnitName)
                    .orElseThrow(() -> new IllegalStateException(
                            "EntityManagerFactory for '" + persistenceUnitName + "' missing from cache"));
            EntityManager managerProxy = EntityManagerProxy.create(persistenceUnitName);
            String backoffKey = singlePersistenceUnit ? "" : persistenceUnitName;

            if (!userProducedFactoryQualifiers.contains(backoffKey)) {
                event.addBean()
                        .beanClass(EntityManagerFactory.class)
                        .scope(Singleton.class)
                        .types(EntityManagerFactory.class, Object.class)
                        .qualifiers(syntheticQualifiers(persistenceUnitName, singlePersistenceUnit))
                        .produceWith(instance -> factory);
            }

            if (!userProducedManagerQualifiers.contains(backoffKey)) {
                event.addBean()
                        .beanClass(EntityManager.class)
                        .scope(Singleton.class)
                        .types(EntityManager.class, Object.class)
                        .qualifiers(syntheticQualifiers(persistenceUnitName, singlePersistenceUnit))
                        .produceWith(instance -> managerProxy);
            }
        }

        event.addBean()
                .beanClass(UserTransaction.class)
                .scope(Singleton.class)
                .types(UserTransaction.class, Object.class)
                .qualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE)
                .produceWith(instance -> new UserTransactionImpl());

        event.addContext(new TransactionScopedContext());
    }

    private static TestContext activeContextOrNull() {
        try {
            return TestContext.get();
        } catch (IllegalStateException notInBootstrap) {
            return null;
        }
    }

    private static Set<String> filterFromAnnotation(PersistenceConfig persistenceConfig) {
        if (persistenceConfig == null || persistenceConfig.persistenceUnits().length == 0) {
            return Collections.emptySet();
        }
        return new HashSet<>(Arrays.asList(persistenceConfig.persistenceUnits()));
    }

    private Map<String, Object> computeProperties(
            ParsedPersistenceUnit unit,
            PersistenceConfig persistenceConfig,
            Class<?> testClass,
            ClassLoader classLoader) {
        Map<String, Object> properties = new LinkedHashMap<>();
        boolean fileMode = persistenceConfig != null && persistenceConfig.fileMode();
        if (fileMode) {
            String filePath = persistenceConfig.filePath().isEmpty()
                    ? defaultFilePath(testClass)
                    : persistenceConfig.filePath();
            properties.put("jakarta.persistence.jdbc.url",
                    "jdbc:h2:file:" + filePath + "/" + unit.name());
        } else {
            properties.put("jakarta.persistence.jdbc.url",
                    "jdbc:h2:mem:" + unit.name() + ";DB_CLOSE_DELAY=-1");
        }
        properties.put("jakarta.persistence.jdbc.user", "sa");
        properties.put("jakarta.persistence.jdbc.password", "");
        properties.put("jakarta.persistence.jdbc.driver", "org.h2.Driver");
        properties.put("jakarta.persistence.schema-generation.database.action", "drop-and-create");

        if (!unit.hasClassElements()) {
            List<Class<?>> discovered = discoverEntityClasses(classLoader);
            if (!discovered.isEmpty()) {
                properties.put("hibernate.loaded.classes", discovered);
            }
        }

        Config config = ConfigProvider.getConfig();
        for (String key : config.getPropertyNames()) {
            if (!key.startsWith(PERSISTENCE_PROPERTY_PREFIX)) {
                continue;
            }
            String propertyName = key.substring(PERSISTENCE_PROPERTY_PREFIX.length());
            config.getOptionalValue(key, String.class).ifPresent(value -> properties.put(propertyName, value));
        }

        PersistencePropertyResolver resolver = TestContext.loadService(PersistencePropertyResolver.class);
        if (resolver != null) {
            Map<String, Object> contributed = resolver.resolvePropertiesFor(unit.name());
            if (contributed != null) {
                properties.putAll(contributed);
            }
        }

        return properties;
    }

    private static List<Class<?>> discoverEntityClasses(ClassLoader classLoader) {
        Set<String> excludes = readProtectedPackagePrefixes();
        Set<String> entityNames = EntityScanner.scan(excludes);
        List<Class<?>> classes = new ArrayList<>();
        for (String name : entityNames) {
            try {
                classes.add(Class.forName(name, false, classLoader));
            } catch (ClassNotFoundException ignored) {
                // class file present on disk but not loadable through
                // this classloader; skipped silently.
            }
        }
        return classes;
    }

    private static Set<String> readProtectedPackagePrefixes() {
        Config config = ConfigProvider.getConfig();
        return config.getOptionalValue(PROTECTED_PACKAGES_KEY, String.class)
                .or(() -> config.getOptionalValue(PROTECTED_PACKAGES_KEY.replace('.', '_'), String.class))
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
                .orElseGet(EntityScanner::defaultExcludedPackagePrefixes);
    }

    private static String defaultFilePath(Class<?> testClass) {
        Config config = ConfigProvider.getConfig();
        String appLabel = config.getOptionalValue(APP_LABEL_KEY, String.class)
                .or(() -> config.getOptionalValue(APP_LABEL_KEY.replace('.', '_'), String.class))
                .orElseGet(testClass::getSimpleName);
        return System.getProperty("user.home") + "/" + appLabel + "_db";
    }

    private static <T> void rewriteField(
            AnnotatedFieldConfigurator<? super T> field,
            String unitName,
            Class<? extends Annotation> jpaAnnotation) {
        field.remove(annotation -> annotation.annotationType().equals(jpaAnnotation));
        field.add(InjectLiteral.INSTANCE);
        if (!unitName.isEmpty()) {
            field.add(NamedLiteral.of(unitName));
        }
    }

    private static String producerNameKey(Set<Annotation> qualifiers) {
        for (Annotation qualifier : qualifiers) {
            if (qualifier instanceof Named named) {
                return named.value();
            }
        }
        return "";
    }

    private static Annotation[] syntheticQualifiers(String persistenceUnitName, boolean singlePersistenceUnit) {
        if (singlePersistenceUnit) {
            return new Annotation[] {Default.Literal.INSTANCE, Any.Literal.INSTANCE};
        }
        return new Annotation[] {NamedLiteral.of(persistenceUnitName), Any.Literal.INSTANCE};
    }
}
