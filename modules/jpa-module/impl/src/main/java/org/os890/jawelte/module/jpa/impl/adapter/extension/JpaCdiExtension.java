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

import org.hibernate.jpa.HibernatePersistenceProvider;
import org.os890.jawelte.core.api.port.ConfigResolver;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;
import org.os890.jawelte.module.jpa.api.ReadOnly;
import org.os890.jawelte.module.jpa.api.port.EntityScanner;
import org.os890.jawelte.module.jpa.api.port.PersistencePropertyResolver;
import org.os890.jawelte.module.jpa.api.port.TransactionStrategy;
import org.os890.jawelte.module.jpa.impl.adapter.context.TransactionScopedContext;
import org.os890.jawelte.module.jpa.impl.config.JpaConfig;
import org.os890.jawelte.module.jpa.impl.util.EmfCache;
import org.os890.jawelte.module.jpa.impl.util.EntityManagerProxy;
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

    private static final String APP_LABEL_KEY = "org.os890.jawelte.module.jpa.app-label";

    private static final String PROTECTED_PACKAGES_KEY =
            "org.os890.jawelte.module.jpa.api.PersistenceConfig.protected-packages";

    /**
     * MicroProfile Config key whose value (comma-separated) lists
     * package prefixes that are <em>exempt</em> from the
     * vendor-internal CDI-bean vetoing observer. Keep set when a
     * downstream module legitimately ships beans in
     * {@code org.apache.geronimo.transaction.*} that the user wants
     * registered.
     */
    private static final String VENDOR_VETO_ALLOWLIST_KEY =
            "org.os890.jawelte.module.jpa.vendor-veto.allowlist.packages";

    /**
     * Marker class shipped by Narayana's CDI integration jar.
     * Presence on the classpath indicates Narayana ships its own
     * {@code @TransactionScoped} {@code Context} and its own
     * {@code @Transactional} interceptors — we delegate to them
     * rather than register competing ones from jpa-module. The same
     * delegation pattern will apply to Quarkus (which embeds Narayana)
     * once TICKET-015 lands.
     */
    private static final String NARAYANA_CDI_EXTENSION_CLASS =
            "com.arjuna.ats.jta.cdi.TransactionExtension";

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
     * duplicate-bean conflicts. Geronimo doesn't ship a CDI integration
     * we want to delegate to, so its CDI beans (if any sneak in via
     * transitives) stay vetoed. Narayana's CDI beans are kept — we
     * delegate context + interceptor to its bundled extension when
     * it's on the classpath. An allowlist via
     * {@link #VENDOR_VETO_ALLOWLIST_KEY} exempts specific packages
     * when a downstream module actually wants them registered.
     */
    private static final Set<String> VENDOR_VETO_PACKAGE_PREFIXES = Set.of(
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

        List<ParsedPersistenceUnit> parsed =
                PersistenceXmlParser.parseAll(Thread.currentThread().getContextClassLoader());
        Set<String> filter = filterFromAnnotation(persistenceConfig);
        // Resolve the active strategy's transaction type once: under
        // JTA the auto-discovery (Hibernate) path bootstraps the EMF
        // with PersistenceUnitTransactionType.JTA; under RESOURCE_LOCAL
        // it stays RESOURCE_LOCAL. The spec bootstrap path picks up
        // the same change from properties (PersistencePropertyResolver
        // contributes jakarta.persistence.transaction-type=JTA when
        // the JTA strategy is active).
        PersistenceUnitTransactionType emfTransactionType = resolveEmfTransactionType();

        Set<String> resolvedActivePersistenceUnits = new LinkedHashSet<>();
        for (ParsedPersistenceUnit unit : parsed) {
            if (!filter.isEmpty() && !filter.contains(unit.name())) {
                continue;
            }
            resolvedActivePersistenceUnits.add(unit.name());
            Map<String, Object> properties = computeProperties(unit, persistenceConfig, testClass);
            persistenceUnitProperties.put(unit.name(), properties);
            EmfCache.getOrCreate(unit.name(),
                    () -> bootstrapEntityManagerFactory(unit, properties, emfTransactionType));
        }
        activePersistenceUnits = resolvedActivePersistenceUnits;
        JpaActivePersistenceUnits.set(activePersistenceUnits);

        // When a vendor JTA CDI integration is on the classpath
        // (Narayana today, Quarkus later) we delegate the @Transactional
        // interceptor to it: Jakarta-EE rollback rules from the
        // platform, no double-interception. Our @ReadOnly interceptor
        // is project-specific, so it stays bound either way.
        if (!platformProvidesCdiTransactionalInterceptor()) {
            event.addInterceptorBinding(Transactional.class);
        }
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

        // The active TransactionStrategy contributes the synthetic
        // UserTransaction bean: RESOURCE_LOCAL ships a delegating
        // helper that drives this same strategy; JTA ships the JTA
        // implementation's standard UserTransaction so consumers see
        // the real Jakarta-EE shape (Test Scenario 20).
        event.addBean()
                .beanClass(UserTransaction.class)
                .scope(ApplicationScoped.class)
                .types(UserTransaction.class, Object.class)
                .qualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE)
                .produceWith(instance -> TestContext.loadService(TransactionStrategy.class).userTransaction());

        // Same delegation rationale as the @Transactional interceptor
        // binding: when Narayana (or in future Quarkus) is on the
        // classpath, its bundled extension already adds a
        // @TransactionScoped Context. Adding our own would either
        // win the bean lookup race and break the vendor's
        // TransactionContext.isActive() (which the vendor's
        // interceptor calls) or lose it and leave our context unused.
        if (!platformProvidesCdiTransactionScopedContext()) {
            event.addContext(new TransactionScopedContext());
        }
    }

    /**
     * Detect whether a vendor JTA CDI integration is on the classpath
     * that ships its own {@code @Transactional} interceptor. Tests
     * for Narayana's integration class today; the same hook will
     * cover Quarkus once TICKET-015 lands (Quarkus embeds Narayana).
     * Resolved via {@link Class#forName(String, boolean, ClassLoader)}
     * against the TCCL — no compile-time dependency on the vendor jar.
     */
    private static boolean platformProvidesCdiTransactionalInterceptor() {
        return classExists(NARAYANA_CDI_EXTENSION_CLASS);
    }

    /**
     * Detect whether a vendor JTA CDI integration registers a
     * {@code @TransactionScoped} {@code Context}. Same probe as
     * {@link #platformProvidesCdiTransactionalInterceptor()} — Narayana's
     * extension does both in one go.
     */
    private static boolean platformProvidesCdiTransactionScopedContext() {
        return classExists(NARAYANA_CDI_EXTENSION_CLASS);
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            return true;
        } catch (ClassNotFoundException notPresent) {
            return false;
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
        return resolver().resolve(VENDOR_VETO_ALLOWLIST_KEY)
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

    /**
     * Pre-CDI lookup of the active {@link ConfigResolver}. CDI is
     * still in {@code BeforeBeanDiscovery} when this Extension's
     * observers fire, so the standard {@code @Inject ConfigResolver}
     * channel isn't available yet — we route through
     * {@link TestContext#loadService(Class)}, which uses the same
     * prioritized SPI lookup the rest of the framework uses for
     * pre-CDI port resolution. The returned resolver applies the
     * dot-or-underscore key fallback internally, so callers feed it
     * a single canonical dot-key per config-key constant.
     */
    private static ConfigResolver resolver() {
        return TestContext.loadService(ConfigResolver.class);
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
            Class<?> testClass) {
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

        // Route the persistence-property prefix walk through JpaConfig (and
        // therefore through the active ConfigResolver) so a consumer-supplied
        // resolver controls every key jpa-module reads — including this prefix
        // (punch-list §5.4).
        properties.putAll(new JpaConfig().additionalPersistenceProperties());

        PersistencePropertyResolver resolver = TestContext.loadService(PersistencePropertyResolver.class);
        if (resolver != null) {
            Map<String, Object> contributed = resolver.resolvePropertiesFor(unit.name(), properties);
            if (contributed != null) {
                properties.putAll(contributed);
            }
        }

        // When a resolver contributed a jtaDataSource, drop the plain
        // JDBC connection coordinates so Hibernate cannot fall back to
        // its non-XA connection-provider path for schema-generation /
        // pool-warm-up. user + password are kept — Hibernate still
        // uses them for DDL execution against the wrapped DataSource.
        if (properties.containsKey("jakarta.persistence.jtaDataSource")) {
            properties.remove("jakarta.persistence.jdbc.url");
            properties.remove("jakarta.persistence.jdbc.driver");
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
     * the container API accepts. The scanner picks the calling thread's
     * context classloader internally — we deliberately don't accept a
     * classloader parameter here, since smuggling one in would let
     * callers override the TCCL the rest of the bootstrap relies on.
     *
     * @param unit            the parsed persistence unit
     * @param properties      merged property bag (H2 + MP Config + resolver)
     * @param transactionType the {@code PersistenceUnitTransactionType} the
     *                        active {@code TransactionStrategy} reports —
     *                        JTA when {@code jta-module} is on the
     *                        classpath, RESOURCE_LOCAL otherwise
     * @return the bootstrapped {@link EntityManagerFactory}
     */
    private static EntityManagerFactory bootstrapEntityManagerFactory(
            ParsedPersistenceUnit unit,
            Map<String, Object> properties,
            PersistenceUnitTransactionType transactionType) {
        if (unit.hasClassElements()) {
            return Persistence.createEntityManagerFactory(unit.name(), properties);
        }
        EntityScanner entityScanner = TestContext.loadService(EntityScanner.class);
        Set<String> scannedEntityNames = entityScanner.scan(
                readProtectedPackagePrefixes(entityScanner), readEntityScanWhitelist());
        java.util.LinkedHashSet<String> mergedEntities = new java.util.LinkedHashSet<>(unit.classes());
        mergedEntities.addAll(scannedEntityNames);
        Properties propertiesAsJavaProperties = new Properties();
        propertiesAsJavaProperties.putAll(properties);
        TestPersistenceUnitInfo unitInfo = new TestPersistenceUnitInfo(
                unit.name(),
                List.copyOf(mergedEntities),
                List.of(),
                propertiesAsJavaProperties,
                transactionType);
        return new HibernatePersistenceProvider().createContainerEntityManagerFactory(unitInfo, properties);
    }

    /**
     * Read the active {@code TransactionStrategy}'s transaction type
     * and convert from the public {@code jakarta.persistence}
     * enum (returned by the SPI) to the {@code jakarta.persistence.spi}
     * enum {@link TestPersistenceUnitInfo} consumes. The two enums
     * have identical names — {@code JTA} / {@code RESOURCE_LOCAL} —
     * so {@code valueOf} round-trips cleanly.
     */
    private static PersistenceUnitTransactionType resolveEmfTransactionType() {
        TransactionStrategy strategy = TestContext.loadService(TransactionStrategy.class);
        return PersistenceUnitTransactionType.valueOf(strategy.getTransactionType().name());
    }

    private static EntityScanner.Whitelist readEntityScanWhitelist() {
        ConfigResolver resolver = resolver();
        List<String> literals = readCsvList(resolver, ENTITY_SCAN_WHITELIST_PACKAGES_KEY);
        List<String> patternStrings = readCsvList(resolver, ENTITY_SCAN_WHITELIST_PATTERNS_KEY);
        if (literals.isEmpty() && patternStrings.isEmpty()) {
            return EntityScanner.Whitelist.empty();
        }
        List<Pattern> compiled = new ArrayList<>(patternStrings.size());
        for (String regex : patternStrings) {
            compiled.add(Pattern.compile(regex));
        }
        return new EntityScanner.Whitelist(List.copyOf(literals), List.copyOf(compiled));
    }

    private static List<String> readCsvList(ConfigResolver resolver, String key) {
        return resolver.resolve(key)
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

    private static Set<String> readProtectedPackagePrefixes(EntityScanner entityScanner) {
        return resolver().resolve(PROTECTED_PACKAGES_KEY)
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
                .orElseGet(entityScanner::defaultExcludedPackagePrefixes);
    }

    private static String defaultFilePath(Class<?> testClass) {
        String appLabel = resolver().resolve(APP_LABEL_KEY).orElseGet(testClass::getSimpleName);
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
