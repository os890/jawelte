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
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceUnit;
import jakarta.persistence.spi.PersistenceUnitTransactionType;
import jakarta.transaction.Transactional;
import jakarta.transaction.UserTransaction;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.hibernate.jpa.HibernatePersistenceProvider;
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
import org.os890.jawelte.module.jpa.impl.util.TestPersistenceUnitInfo;

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
     * MicroProfile Config keys for the optional entity-scan whitelist.
     * When at least one of the two is set (and non-empty), the
     * {@link EntityScanner} drops every FQCN that doesn't match a
     * literal prefix or a regex pattern from the configured list.
     * Both keys accept comma-separated values; an unset key defaults
     * to "no entries". With both unset, no whitelist filtering applies.
     */
    private static final String ENTITY_SCAN_WHITELIST_PACKAGES_KEY =
            "org.os890.jawelte.module.jpa.entity-scan.whitelist.packages";
    private static final String ENTITY_SCAN_WHITELIST_PATTERNS_KEY =
            "org.os890.jawelte.module.jpa.entity-scan.whitelist.patterns";

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
            EmfCache.getOrCreate(unit.name(), () -> bootstrapEntityManagerFactory(unit, properties, classLoader));
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
                        .scope(ApplicationScoped.class)
                        .types(EntityManagerFactory.class, Object.class)
                        .qualifiers(syntheticQualifiers(persistenceUnitName, singlePersistenceUnit))
                        .produceWith(instance -> factory);
            }

            if (!userProducedManagerQualifiers.contains(backoffKey)) {
                event.addBean()
                        .beanClass(EntityManager.class)
                        .scope(ApplicationScoped.class)
                        .types(EntityManager.class, Object.class)
                        .qualifiers(syntheticQualifiers(persistenceUnitName, singlePersistenceUnit))
                        .produceWith(instance -> managerProxy);
            }
        }

        event.addBean()
                .beanClass(UserTransaction.class)
                .scope(ApplicationScoped.class)
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
            // Append the test-class simple name to the file path so
            // two test classes that share a PU name don't collide on
            // the same H2 file. AUTO_SERVER=TRUE lets the developer
            // open the file from a separate H2 console process while
            // the test JVM still holds it.
            properties.put("jakarta.persistence.jdbc.url",
                    "jdbc:h2:file:" + filePath + "/" + unit.name() + "_" + testClass.getSimpleName()
                            + ";DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE");
        } else {
            properties.put("jakarta.persistence.jdbc.url",
                    "jdbc:h2:mem:" + unit.name() + ";DB_CLOSE_DELAY=-1");
        }
        properties.put("jakarta.persistence.jdbc.user", "sa");
        properties.put("jakarta.persistence.jdbc.password", "");
        properties.put("jakarta.persistence.jdbc.driver", "org.h2.Driver");
        properties.put("jakarta.persistence.schema-generation.database.action", "drop-and-create");

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

    /**
     * Decide between the standard JPA bootstrap path and the
     * Hibernate-specific {@code createContainerEntityManagerFactory}
     * path with a custom {@link TestPersistenceUnitInfo}: when the
     * persistence unit declares no {@code <class>} elements,
     * Hibernate cannot scan for {@code @Entity} types outside of an
     * application server, so we run our own ASM scanner and feed the
     * resulting class-name list through the {@code PersistenceUnitInfo}
     * the container API accepts.
     *
     * @param unit         the parsed persistence unit
     * @param properties   merged property bag (H2 + MP Config + resolver)
     * @param classLoader  the classloader to use for entity discovery
     * @return the bootstrapped {@link EntityManagerFactory}
     */
    private static EntityManagerFactory bootstrapEntityManagerFactory(
            ParsedPersistenceUnit unit,
            Map<String, Object> properties,
            ClassLoader classLoader) {
        if (unit.hasClassElements()) {
            return Persistence.createEntityManagerFactory(unit.name(), properties);
        }
        Set<String> scannedEntityNames = EntityScanner.scan(
                readProtectedPackagePrefixes(), readEntityScanWhitelist());
        java.util.LinkedHashSet<String> mergedEntities = new java.util.LinkedHashSet<>(unit.classes());
        mergedEntities.addAll(scannedEntityNames);
        Properties propertiesAsJavaProperties = new Properties();
        propertiesAsJavaProperties.putAll(properties);
        TestPersistenceUnitInfo unitInfo = new TestPersistenceUnitInfo(
                unit.name(),
                List.copyOf(mergedEntities),
                List.of(),
                propertiesAsJavaProperties,
                PersistenceUnitTransactionType.RESOURCE_LOCAL);
        return new HibernatePersistenceProvider().createContainerEntityManagerFactory(unitInfo, properties);
    }

    private static EntityScanner.Whitelist readEntityScanWhitelist() {
        Config config = ConfigProvider.getConfig();
        List<String> literals = readCsvList(config, ENTITY_SCAN_WHITELIST_PACKAGES_KEY);
        List<String> patternStrings = readCsvList(config, ENTITY_SCAN_WHITELIST_PATTERNS_KEY);
        if (literals.isEmpty() && patternStrings.isEmpty()) {
            return EntityScanner.Whitelist.empty();
        }
        List<Pattern> compiled = new ArrayList<>(patternStrings.size());
        for (String regex : patternStrings) {
            compiled.add(Pattern.compile(regex));
        }
        return new EntityScanner.Whitelist(List.copyOf(literals), List.copyOf(compiled));
    }

    private static List<String> readCsvList(Config config, String key) {
        return config.getOptionalValue(key, String.class)
                .or(() -> config.getOptionalValue(key.replace('.', '_'), String.class))
                .map(value -> {
                    List<String> entries = new ArrayList<>();
                    for (String entry : value.split(",")) {
                        String trimmed = entry.trim();
                        if (!trimmed.isEmpty()) {
                            entries.add(trimmed);
                        }
                    }
                    return entries;
                })
                .orElseGet(List::of);
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
