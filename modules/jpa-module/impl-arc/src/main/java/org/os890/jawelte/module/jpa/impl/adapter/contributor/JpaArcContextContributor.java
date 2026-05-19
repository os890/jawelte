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
package org.os890.jawelte.module.jpa.impl.adapter.contributor;

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
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceUnit;
import jakarta.persistence.spi.PersistenceUnitTransactionType;
import jakarta.transaction.UserTransaction;

import org.hibernate.jpa.HibernatePersistenceProvider;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.DotName;
import org.os890.jawelte.core.api.port.ConfigResolver;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.impl.spi.ArcContextContributor;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;
import org.os890.jawelte.module.jpa.api.port.CdiTransactionalSupportProvider;
import org.os890.jawelte.module.jpa.api.port.EntityScanner;
import org.os890.jawelte.module.jpa.api.port.PersistencePropertyResolver;
import org.os890.jawelte.module.jpa.api.port.TransactionStrategy;
import org.os890.jawelte.module.jpa.impl.adapter.context.TransactionScopedContext;
import org.os890.jawelte.module.jpa.impl.util.DeferredExtendedBeanManager;
import org.os890.jawelte.module.jpa.impl.util.EmfCache;
import org.os890.jawelte.module.jpa.impl.util.EntityManagerProxy;
import org.os890.jawelte.module.jpa.impl.util.JpaActivePersistenceUnits;
import org.os890.jawelte.module.jpa.impl.util.PersistenceXmlParser;
import org.os890.jawelte.module.jpa.impl.util.PersistenceXmlParser.ParsedPersistenceUnit;
import org.os890.jawelte.module.jpa.impl.util.TestPersistenceUnitInfo;

import io.quarkus.arc.processor.BeanProcessor;
import io.quarkus.arc.processor.DotNames;

/**
 * jpa-module's {@link ArcContextContributor}. Replaces the previous
 * {@code JpaCdiExtension} (a CDI portable extension, unsupported by
 * Quarkus ArC) with the ArC-native equivalents:
 *
 * <ul>
 *   <li>Pre-warm {@code EntityManagerFactory}s, populate
 *       {@link JpaActivePersistenceUnits}, and bind the
 *       {@link DeferredExtendedBeanManager} on {@link TestContext} —
 *       all before {@code BeanProcessor.process()} runs (matches the
 *       prior {@code BeforeBeanDiscovery} timing).</li>
 *   <li>Add a Jandex {@link AnnotationTransformation} that rewrites
 *       every {@code @PersistenceContext} / {@code @PersistenceUnit}
 *       field on every indexed type to {@code @Inject} (plus
 *       {@code @Named} when a {@code unitName} is supplied),
 *       replacing the prior {@code @Observes ProcessAnnotatedType}
 *       observer.</li>
 *   <li>Add a {@link io.quarkus.arc.processor.BeanRegistrar} that
 *       registers a synthetic {@link EntityManagerFactory} and
 *       {@link EntityManager} per active persistence unit (subject
 *       to user-producer back-off, detected by scanning the
 *       already-collected beans for matching producers), plus a
 *       synthetic {@link UserTransaction} — replacing the prior
 *       {@code @Observes AfterBeanDiscovery} bean-adding code.</li>
 *   <li>Add a {@link io.quarkus.arc.processor.ContextRegistrar} that
 *       registers {@link TransactionScopedContext} for
 *       {@code @TransactionScoped} — unless the active
 *       {@link CdiTransactionalSupportProvider} reports that a
 *       platform-provided context is already on the classpath.</li>
 * </ul>
 *
 * <p>Discovered via
 * {@code META-INF/services/org.os890.jawelte.module.cdi.impl.spi.ArcContextContributor}.
 *
 * <p>The deferred runtime-{@code BeanManager} notification that the
 * prior extension's {@code @Initialized(ApplicationScoped.class)}
 * observer performed now happens in
 * {@code JpaLifecycleAdapter.beforeAll}, which runs after
 * {@code Arc.initialize}.
 */
public class JpaArcContextContributor implements ArcContextContributor {

    private static final DotName PERSISTENCE_CONTEXT_DOT =
            DotName.createSimple(PersistenceContext.class.getName());
    private static final DotName PERSISTENCE_UNIT_DOT =
            DotName.createSimple(PersistenceUnit.class.getName());
    private static final DotName INJECT_DOT = DotNames.INJECT;
    private static final DotName NAMED_DOT = DotName.createSimple("jakarta.inject.Named");

    private static final String SCAN_EXCLUDE_PACKAGES_KEY =
            "org.os890.jawelte.module.jpa.scan-exclude-packages";
    private static final String ENTITY_SCAN_WHITELIST_PACKAGES_KEY =
            "org.os890.jawelte.module.jpa.entity-scan.whitelist.packages";
    private static final String ENTITY_SCAN_WHITELIST_PATTERNS_KEY =
            "org.os890.jawelte.module.jpa.entity-scan.whitelist.patterns";
    private static final String PERSISTENCE_PROPERTY_PREFIX =
            "org.os890.jawelte.module.jpa.persistence-property.";

    /** No-arg constructor required by {@code ServiceLoader}. */
    public JpaArcContextContributor() {
    }

    @Override
    public void contribute(TestContext testContext, BeanProcessor.Builder builder) {
        Class<?> testClass = testContext.getTestClass();
        PersistenceConfig persistenceConfig = testClass.getAnnotation(PersistenceConfig.class);

        DeferredExtendedBeanManager deferredBeanManager = new DeferredExtendedBeanManager();
        testContext.bindMetadata(DeferredExtendedBeanManager.class, deferredBeanManager);

        List<ParsedPersistenceUnit> parsed = PersistenceXmlParser.parseAll(
                Thread.currentThread().getContextClassLoader());
        Set<String> filter = filterFromAnnotation(persistenceConfig);
        PersistenceUnitTransactionType emfTransactionType = resolveEmfTransactionType();

        Set<String> activePersistenceUnits = new LinkedHashSet<>();
        for (ParsedPersistenceUnit unit : parsed) {
            if (!filter.isEmpty() && !filter.contains(unit.name())) {
                continue;
            }
            activePersistenceUnits.add(unit.name());
            Map<String, Object> properties = computeProperties(unit, persistenceConfig, testClass);
            properties.put("jakarta.persistence.bean.manager", deferredBeanManager);
            EmfCache.getOrCreate(unit.name(),
                    () -> bootstrapEntityManagerFactory(unit, properties, emfTransactionType));
        }
        JpaActivePersistenceUnits.set(activePersistenceUnits);

        builder.addAnnotationTransformation(persistenceFieldRewrite());
        builder.addBeanRegistrar(new JpaSyntheticBeanRegistrar(activePersistenceUnits));

        // @jakarta.transaction.Transactional declares its value() / rollbackOn()
        // / dontRollbackOn() members as @Nonbinding, so the interceptor's
        // @Transactional binding ought to match a method's
        // @Transactional(REQUIRES_NEW) without considering the members.
        // ArC's default discovery doesn't surface that @Nonbinding intent
        // unless the binding is explicitly registered as such, leaving
        // nested @Transactional(REQUIRES_NEW) methods un-intercepted.
        // Register the binding with all three members marked nonbinding
        // so the interceptor fires regardless of the chosen TxType.
        // @ReadOnly mirrored alongside so standalone-ArC fires the
        // ReadOnlyInterceptor without the jpa-module/deployment
        // build step (which only fires under @QuarkusTest).
        builder.addInterceptorBindingRegistrar(new io.quarkus.arc.processor.InterceptorBindingRegistrar() {
            @Override
            public List<io.quarkus.arc.processor.InterceptorBindingRegistrar.InterceptorBinding>
                    getAdditionalBindings() {
                return List.of(
                        io.quarkus.arc.processor.InterceptorBindingRegistrar.InterceptorBinding.of(
                                DotName.createSimple("jakarta.transaction.Transactional"),
                                Set.of("value", "rollbackOn", "dontRollbackOn")),
                        io.quarkus.arc.processor.InterceptorBindingRegistrar.InterceptorBinding.of(
                                DotName.createSimple("org.os890.jawelte.module.jpa.api.ReadOnly")));
            }
        });

        // Strip the value()/rollbackOn()/dontRollbackOn() members from
        // every @Transactional annotation on methods/classes so the
        // interceptor's default-shape binding always matches at ArC
        // bean processing time. jpa-module's TransactionalInterceptor
        // ignores TxType anyway (every call is treated as REQUIRED,
        // with the JtaTransactionStrategy suspending the outer for
        // nesting — effectively REQUIRES_NEW). Without this rewrite,
        // ArC's interceptor binding matcher leaves
        // @Transactional(REQUIRES_NEW)-annotated methods un-intercepted
        // under standalone-ArC.
        DotName transactionalDot = DotName.createSimple("jakarta.transaction.Transactional");
        builder.addAnnotationTransformation(AnnotationTransformation.forMethods()
                .whenAnyMatch(ann -> ann.name().equals(transactionalDot))
                .transform(ctx -> {
                    ctx.remove(ann -> ann.name().equals(transactionalDot));
                    ctx.add(AnnotationInstance.builder(transactionalDot).build());
                }));
        builder.addAnnotationTransformation(AnnotationTransformation.forClasses()
                .whenAnyMatch(ann -> ann.name().equals(transactionalDot))
                .transform(ctx -> {
                    ctx.remove(ann -> ann.name().equals(transactionalDot));
                    ctx.add(AnnotationInstance.builder(transactionalDot).build());
                }));

        if (!cdiTransactionalSupport().platformProvidesTransactionScopedContext()) {
            builder.addContextRegistrar(registration -> registration
                    .configure(jakarta.transaction.TransactionScoped.class)
                    .normal()
                    .creator(TransactionScopedContextCreator.class)
                    .done());
        }
    }

    private static AnnotationTransformation persistenceFieldRewrite() {
        return AnnotationTransformation.forFields()
                .whenAnyMatch(ann -> ann.name().equals(PERSISTENCE_CONTEXT_DOT)
                        || ann.name().equals(PERSISTENCE_UNIT_DOT))
                .transform(ctx -> {
                    String unitName = null;
                    for (AnnotationInstance ann : ctx.annotations()) {
                        if (ann.name().equals(PERSISTENCE_CONTEXT_DOT)
                                || ann.name().equals(PERSISTENCE_UNIT_DOT)) {
                            org.jboss.jandex.AnnotationValue value = ann.value("unitName");
                            if (value != null) {
                                String resolved = value.asString();
                                if (resolved != null && !resolved.isEmpty()) {
                                    unitName = resolved;
                                }
                            }
                        }
                    }
                    ctx.remove(ann -> ann.name().equals(PERSISTENCE_CONTEXT_DOT)
                            || ann.name().equals(PERSISTENCE_UNIT_DOT));
                    ctx.add(AnnotationInstance.builder(INJECT_DOT).build());
                    if (unitName != null) {
                        ctx.add(AnnotationInstance.builder(NAMED_DOT)
                                .value(unitName).build());
                    }
                });
    }

    private static Set<String> filterFromAnnotation(PersistenceConfig persistenceConfig) {
        if (persistenceConfig == null || persistenceConfig.persistenceUnits().length == 0) {
            return Collections.emptySet();
        }
        return new HashSet<>(Arrays.asList(persistenceConfig.persistenceUnits()));
    }

    private static Map<String, Object> computeProperties(
            ParsedPersistenceUnit unit,
            PersistenceConfig persistenceConfig,
            Class<?> testClass) {
        Map<String, Object> properties = new LinkedHashMap<>();
        boolean fileMode = persistenceConfig != null && persistenceConfig.fileMode();
        if (fileMode) {
            String filePath = persistenceConfig.filePath().isEmpty()
                    ? defaultFilePath(testClass)
                    : persistenceConfig.filePath();
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

        properties.putAll(readAdditionalPersistenceProperties());

        PersistencePropertyResolver resolver = TestContext.loadService(PersistencePropertyResolver.class);
        if (resolver != null) {
            Map<String, Object> contributed = resolver.resolvePropertiesFor(unit.name(), properties);
            if (contributed != null) {
                properties.putAll(contributed);
            }
        }

        if (properties.containsKey("jakarta.persistence.jtaDataSource")) {
            properties.remove("jakarta.persistence.jdbc.url");
            properties.remove("jakarta.persistence.jdbc.driver");
        }

        return properties;
    }

    private static EntityManagerFactory bootstrapEntityManagerFactory(
            ParsedPersistenceUnit unit,
            Map<String, Object> properties,
            PersistenceUnitTransactionType transactionType) {
        if (unit.hasClassElements()) {
            return Persistence.createEntityManagerFactory(unit.name(), properties);
        }
        EntityScanner entityScanner = TestContext.loadService(EntityScanner.class);
        Set<String> scannedEntityNames = entityScanner.scan(
                readScanExcludePackages(), readEntityScanWhitelist());
        LinkedHashSet<String> mergedEntities = new LinkedHashSet<>(unit.classes());
        mergedEntities.addAll(scannedEntityNames);
        Properties propertiesAsJavaProperties = new Properties();
        propertiesAsJavaProperties.putAll(properties);
        TestPersistenceUnitInfo unitInfo = new TestPersistenceUnitInfo(
                unit.name(),
                List.copyOf(mergedEntities),
                List.of(),
                propertiesAsJavaProperties,
                transactionType);
        return new HibernatePersistenceProvider()
                .createContainerEntityManagerFactory(unitInfo, properties);
    }

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

    private static Set<String> readScanExcludePackages() {
        return resolver().resolve(SCAN_EXCLUDE_PACKAGES_KEY)
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
                .orElseGet(Set::of);
    }

    private static Map<String, String> readAdditionalPersistenceProperties() {
        ConfigResolver configResolver = resolver();
        Map<String, String> additional = new LinkedHashMap<>();
        for (String key : configResolver.resolveKeys()) {
            if (!key.startsWith(PERSISTENCE_PROPERTY_PREFIX)) {
                continue;
            }
            String propertyName = key.substring(PERSISTENCE_PROPERTY_PREFIX.length());
            configResolver.resolve(key).ifPresent(value -> additional.put(propertyName, value));
        }
        return Map.copyOf(additional);
    }

    private static String defaultFilePath(Class<?> testClass) {
        String appLabel = resolver()
                .resolve("org.os890.jawelte.module.jpa.app-label")
                .orElseGet(testClass::getSimpleName);
        return System.getProperty("user.home") + "/" + appLabel + "_db";
    }

    private static ConfigResolver resolver() {
        return TestContext.loadService(ConfigResolver.class);
    }

    private static CdiTransactionalSupportProvider cdiTransactionalSupport() {
        return TestContext.loadService(CdiTransactionalSupportProvider.class);
    }

    /**
     * {@link io.quarkus.arc.processor.BeanRegistrar} that synthesises
     * one {@link EntityManagerFactory} + one {@link EntityManager}
     * per active persistence unit (RESOURCE_LOCAL path: ApplicationScoped
     * EntityManager proxy), plus a single {@link UserTransaction}.
     * User-provided producers for the same type take precedence —
     * detected by scanning the bean set produced up to this point.
     */
    static class JpaSyntheticBeanRegistrar
            implements io.quarkus.arc.processor.BeanRegistrar {

        private final Set<String> activePersistenceUnits;

        JpaSyntheticBeanRegistrar(Set<String> activePersistenceUnits) {
            this.activePersistenceUnits = activePersistenceUnits;
        }

        @Override
        public void register(RegistrationContext registrationContext) {
            boolean singlePersistenceUnit = activePersistenceUnits.size() == 1;
            Set<String> userProducedFactoryQualifiers = new HashSet<>();
            Set<String> userProducedManagerQualifiers = new HashSet<>();
            for (io.quarkus.arc.processor.BeanInfo bean
                    : registrationContext.beans().producers().collect()) {
                String beanTypeName = bean.getImplClazz() == null
                        ? "" : bean.getImplClazz().name().toString();
                String namedQualifier = namedQualifier(bean.getQualifiers());
                if (bean.getTypes().stream().anyMatch(t -> t.name().toString()
                        .equals(EntityManagerFactory.class.getName()))) {
                    userProducedFactoryQualifiers.add(namedQualifier);
                }
                if (bean.getTypes().stream().anyMatch(t -> t.name().toString()
                        .equals(EntityManager.class.getName()))) {
                    userProducedManagerQualifiers.add(namedQualifier);
                }
                // beanTypeName is unused here but kept for diagnostic
                // purposes if we ever need to log which producer
                // contributed the backoff key.
                if (false) {
                    System.err.println(beanTypeName);
                }
            }

            for (String persistenceUnitName : activePersistenceUnits) {
                EntityManagerFactory factory = EmfCache.getCached(persistenceUnitName)
                        .orElseThrow(() -> new IllegalStateException(
                                "EntityManagerFactory for '" + persistenceUnitName
                                        + "' missing from cache"));
                String backoffKey = singlePersistenceUnit ? "" : persistenceUnitName;

                if (!userProducedFactoryQualifiers.contains(backoffKey)) {
                    registerEntityManagerFactoryBean(
                            registrationContext, persistenceUnitName, factory, singlePersistenceUnit);
                }
                if (!userProducedManagerQualifiers.contains(backoffKey)) {
                    registerEntityManagerBean(
                            registrationContext, persistenceUnitName, singlePersistenceUnit);
                }
            }

            registerUserTransactionBean(registrationContext);
        }

        private static void registerEntityManagerFactoryBean(
                RegistrationContext registrationContext,
                String persistenceUnitName,
                EntityManagerFactory factory,
                boolean singlePersistenceUnit) {
            io.quarkus.arc.processor.BeanConfigurator<Object> configurator =
                    registrationContext.configure(EntityManagerFactory.class)
                            .scope(ApplicationScoped.class)
                            .addType(EntityManagerFactory.class)
                            .creator(EntityManagerFactoryBeanCreator.class)
                            .param("persistenceUnitName", persistenceUnitName);
            applySyntheticQualifiers(configurator, persistenceUnitName, singlePersistenceUnit);
            configurator.done();
        }

        private static void registerEntityManagerBean(
                RegistrationContext registrationContext,
                String persistenceUnitName,
                boolean singlePersistenceUnit) {
            io.quarkus.arc.processor.BeanConfigurator<Object> configurator =
                    registrationContext.configure(EntityManager.class)
                            .scope(ApplicationScoped.class)
                            .addType(EntityManager.class)
                            .creator(EntityManagerBeanCreator.class)
                            .param("persistenceUnitName", persistenceUnitName);
            applySyntheticQualifiers(configurator, persistenceUnitName, singlePersistenceUnit);
            configurator.done();
        }

        private static void registerUserTransactionBean(RegistrationContext registrationContext) {
            registrationContext.configure(UserTransaction.class)
                    .scope(ApplicationScoped.class)
                    .addType(UserTransaction.class)
                    .addQualifier(AnnotationInstance.builder(DotNames.DEFAULT).build())
                    .creator(UserTransactionBeanCreator.class)
                    .done();
        }

        private static void applySyntheticQualifiers(
                io.quarkus.arc.processor.BeanConfigurator<?> configurator,
                String persistenceUnitName,
                boolean singlePersistenceUnit) {
            if (singlePersistenceUnit) {
                configurator.addQualifier(AnnotationInstance.builder(DotNames.DEFAULT).build());
            } else {
                configurator.addQualifier(AnnotationInstance.builder(NAMED_DOT)
                        .value(persistenceUnitName).build());
            }
        }

        private static String namedQualifier(Set<AnnotationInstance> qualifiers) {
            for (AnnotationInstance qualifier : qualifiers) {
                if (qualifier.name().equals(NAMED_DOT)) {
                    org.jboss.jandex.AnnotationValue value = qualifier.value();
                    return value == null ? "" : value.asString();
                }
            }
            return "";
        }
    }

    /** Synthetic-bean creator that returns the cached {@link EntityManagerFactory}. */
    public static class EntityManagerFactoryBeanCreator
            implements io.quarkus.arc.BeanCreator<EntityManagerFactory> {

        /** No-arg constructor required by ArC's reflective creator lookup. */
        public EntityManagerFactoryBeanCreator() {
        }

        @Override
        public EntityManagerFactory create(io.quarkus.arc.SyntheticCreationalContext<EntityManagerFactory> context) {
            String persistenceUnitName = (String) context.getParams().get("persistenceUnitName");
            return EmfCache.getCached(persistenceUnitName).orElseThrow(() -> new IllegalStateException(
                    "EntityManagerFactory for '" + persistenceUnitName + "' missing from cache"));
        }
    }

    /** Synthetic-bean creator that returns the per-PU {@link EntityManagerProxy}. */
    public static class EntityManagerBeanCreator
            implements io.quarkus.arc.BeanCreator<EntityManager> {

        /** No-arg constructor required by ArC's reflective creator lookup. */
        public EntityManagerBeanCreator() {
        }

        @Override
        public EntityManager create(io.quarkus.arc.SyntheticCreationalContext<EntityManager> context) {
            String persistenceUnitName = (String) context.getParams().get("persistenceUnitName");
            return EntityManagerProxy.create(persistenceUnitName);
        }
    }

    /**
     * Synthetic-bean creator that returns the active
     * {@link TransactionStrategy}'s {@link UserTransaction}.
     */
    public static class UserTransactionBeanCreator
            implements io.quarkus.arc.BeanCreator<UserTransaction> {

        /** No-arg constructor required by ArC's reflective creator lookup. */
        public UserTransactionBeanCreator() {
        }

        @Override
        public UserTransaction create(io.quarkus.arc.SyntheticCreationalContext<UserTransaction> context) {
            return TestContext.loadService(TransactionStrategy.class).userTransaction();
        }
    }

    /** ArC {@link io.quarkus.arc.ContextCreator} that returns a fresh {@link TransactionScopedContext}. */
    public static class TransactionScopedContextCreator
            implements io.quarkus.arc.ContextCreator {

        /** No-arg constructor required by ArC's reflective creator lookup. */
        public TransactionScopedContextCreator() {
        }

        @Override
        public io.quarkus.arc.InjectableContext create(Map<String, Object> params) {
            return new TransactionScopedContext();
        }
    }
}
