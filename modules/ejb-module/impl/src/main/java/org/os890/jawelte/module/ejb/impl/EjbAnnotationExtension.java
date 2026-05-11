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
package org.os890.jawelte.module.ejb.impl;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.ejb.Singleton;
import jakarta.ejb.Stateless;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;

import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.UrlSet;
import org.apache.xbean.finder.archive.ClasspathArchive;
import org.os890.jawelte.core.api.port.ConfigResolver;
import org.os890.jawelte.core.api.port.ServicePriorityResolver;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.ejb.api.port.EjbAnnotationMapper;

/**
 * CDI {@link Extension} that drives the {@link EjbAnnotationMapper}
 * chain — the runtime piece of ejb-module.
 *
 * <p>During {@code BeforeBeanDiscovery} the extension:
 * <ul>
 *   <li>Enumerates the available {@link EjbAnnotationMapper} candidates
 *       via {@code ServiceLoader.load(EjbAnnotationMapper.class)} and
 *       sorts them by passing the list to
 *       {@code TestContext.loadService(ServicePriorityResolver.class).sort(...)}.
 *       This is the same precedent {@code JtaTransactionStrategy} uses
 *       for the {@code TransactionManagerProvider} chain.</li>
 *   <li>Splits the sorted chain into the additional mappers
 *       ({@code isAdditionalMapper() == true}, run first) and the
 *       single terminal default ({@code isAdditionalMapper() == false},
 *       run when every additional mapper returned {@code null}).</li>
 *   <li>Registers {@code @jakarta.ejb.Singleton} and
 *       {@code @jakarta.ejb.Stateless} as CDI stereotypes via
 *       {@link BeforeBeanDiscovery#addStereotype(Class, Annotation...)}
 *       so the EJB session-bean annotations carry the EJB baseline
 *       (Singleton → {@code @ApplicationScoped}, Stateless →
 *       {@code @Dependent}) plus the implicit
 *       {@code @jakarta.transaction.Transactional} for every class
 *       the mapper chain claims.</li>
 *   <li>Walks the classpath with {@code xbean-finder} to enumerate
 *       every type carrying {@code @Singleton} or {@code @Stateless}
 *       and feeds each to
 *       {@link BeforeBeanDiscovery#addAnnotatedType(AnnotatedType, String)}.
 *       The CDI 4.0 spec only ENCOURAGES (does not require) runtimes
 *       to treat {@code addStereotype}-registered annotations as
 *       bean-defining for type-discovery purposes; OpenWebBeans and
 *       Weld both stop short of the encouragement, so the only
 *       portable way to make an EJB-only-annotated class discoverable
 *       under {@code bean-discovery-mode="annotated"} is for
 *       ejb-module to enumerate the candidates itself. The xbean
 *       scan reads bytecode without calling {@link Class#forName} on
 *       non-matching classes, so the cost is bounded.</li>
 * </ul>
 *
 * <p>During {@code ProcessAnnotatedType<T>} the extension walks the
 * sorted chain in priority order: the first additional mapper that
 * returns a non-{@code null} result claims the class; the terminal
 * default runs only when every additional mapper returned
 * {@code null}. Non-empty results are applied via
 * {@code configureAnnotatedType().add(...)}.
 *
 * <p>The extension is registered through
 * {@code META-INF/services/jakarta.enterprise.inject.spi.Extension}
 * so the CDI runtime picks it up via the standard service-loader
 * lookup at container start.
 */
public class EjbAnnotationExtension implements Extension {

    /**
     * MP Config key whose comma-separated value lists the
     * class-level annotations ejb-module observes. The two standard
     * EJB session-bean annotations ship as the default value in this
     * module's {@code META-INF/microprofile-config.properties}; a
     * user with a custom {@code EjbAnnotationMapper} (e.g. for
     * {@code @Stateful}) extends the list by overriding the same
     * key in a higher-priority MP Config source.
     *
     * <p>The configured set drives two things at
     * {@code BeforeBeanDiscovery}: the {@code xbean-finder} classpath
     * scan (which annotations make a class discoverable under
     * {@code bean-discovery-mode="annotated"}) and the broad
     * {@code ProcessAnnotatedType} observer's filter (which classes
     * the additional-mapper chain runs against beyond the hardcoded
     * {@code @Singleton} / {@code @Stateless} fast-path).
     */
    public static final String BEAN_DEFINING_ANNOTATIONS_KEY =
            "org.os890.jawelte.module.ejb.bean-defining-annotations";

    /**
     * Logger emitting one entry per class whose
     * {@code AnnotatedType} the extension transformed. Level
     * {@link Level#DEBUG}: silent under default JUL/SLF4J root
     * configurations, but turnable on per-project for
     * "did ejb-module touch this class?" diagnostics.
     */
    private static final Logger LOG = System.getLogger(EjbAnnotationExtension.class.getName());

    /**
     * Packages skipped by the {@code @Singleton} / {@code @Stateless}
     * classpath scan. Same baseline as
     * {@code XbeanFinderEntityScanner.defaultExcludedPackagePrefixes()}
     * — the JDK, the Jakarta APIs, the CDI runtimes, common test-time
     * libraries, jawelte's own packages.
     */
    private static final Set<String> SCAN_EXCLUDE_PREFIXES = Set.of(
            "java.",
            "javax.",
            "jakarta.",
            "org.hibernate.",
            "org.h2.",
            "org.jboss.weld.",
            "org.apache.openwebbeans.",
            "org.apache.webbeans.",
            "org.apache.xbean.",
            "org.mockito.",
            "net.bytebuddy.",
            "org.junit.",
            "org.opentest4j.",
            "io.smallrye.",
            "org.os890.jawelte.core.",
            "org.os890.jawelte.module.");

    /**
     * Annotations hardcoded into the fast-path
     * {@code ProcessAnnotatedType} observer via {@code @WithAnnotations}.
     * The MP Config–driven set
     * ({@link #BEAN_DEFINING_ANNOTATIONS_KEY}) defaults to exactly
     * these two; any additional FQCN configured beyond them flows
     * through the broad observer instead.
     */
    private static final Set<Class<? extends Annotation>> FAST_PATH_ANNOTATIONS =
            Set.of(Singleton.class, Stateless.class);

    /** Additional mappers in priority order, populated on {@code BeforeBeanDiscovery}. */
    private final List<EjbAnnotationMapper> additionalMappers = new ArrayList<>();

    /** Terminal default mapper, populated on {@code BeforeBeanDiscovery}. */
    private EjbAnnotationMapper terminalMapper;

    /**
     * Class-level annotations ejb-module observes, resolved from MP
     * Config at {@code BeforeBeanDiscovery} time. Drives both the
     * {@code xbean-finder} scan and the broad observer's filter.
     */
    private Set<Class<? extends Annotation>> configuredAnnotations = Set.of();

    /**
     * Configured annotations minus the hardcoded fast-path set. The
     * broad observer returns immediately when this is empty, so a
     * deployment that sticks with the defaults pays no per-class
     * cost for the broad observer beyond a single boolean check.
     */
    private Set<Class<? extends Annotation>> extraAnnotations = Set.of();

    /**
     * Required public no-arg constructor for CDI Extension
     * {@code ServiceLoader} instantiation.
     */
    public EjbAnnotationExtension() {
    }

    /**
     * Resolve the mapper chain, register the EJB session-bean
     * annotations as CDI stereotypes, and enumerate every
     * {@code @Singleton} / {@code @Stateless} type on the classpath
     * so {@code bean-discovery-mode="annotated"} archives still
     * surface EJB-annotated classes as CDI candidates.
     *
     * @param event       the in-flight {@code BeforeBeanDiscovery}
     *                    event; non-{@code null}
     * @param beanManager the in-flight {@code BeanManager}, used to
     *                    materialise {@code AnnotatedType} instances
     *                    for the discovered EJB classes;
     *                    non-{@code null}
     */
    void onBeforeBeanDiscovery(@Observes BeforeBeanDiscovery event, BeanManager beanManager) {
        resolveConfiguredAnnotations();
        resolveMapperChain();
        event.addStereotype(Singleton.class,
                ApplicationScoped.Literal.INSTANCE,
                TransactionalLiteral.INSTANCE);
        event.addStereotype(Stateless.class,
                Dependent.Literal.INSTANCE,
                TransactionalLiteral.INSTANCE);
        registerEjbAnnotatedTypes(event, beanManager);
    }

    /**
     * Fast-path observer for the two standard EJB session-bean
     * annotations. CDI restricts delivery to types carrying
     * {@code @jakarta.ejb.Singleton} or {@code @jakarta.ejb.Stateless}
     * via {@link WithAnnotations}, so non-EJB classes never reach the
     * extension here — only the broad observer (below) sees them,
     * and only when MP Config configures annotations beyond this
     * hardcoded pair (see {@link #BEAN_DEFINING_ANNOTATIONS_KEY}).
     *
     * @param event       the in-flight {@code ProcessAnnotatedType}
     *                    event for an EJB-annotated type; non-{@code null}
     * @param beanManager the in-flight {@code BeanManager}, forwarded
     *                    to every mapper in the chain;
     *                    non-{@code null}
     * @param <T>         the annotated type's bean class
     */
    <T> void onProcessEjbAnnotatedType(
            @Observes @WithAnnotations({Singleton.class, Stateless.class})
            ProcessAnnotatedType<T> event,
            BeanManager beanManager) {
        applyChain(event, beanManager, /* runTerminal */ true);
    }

    /**
     * Broad-path observer that runs only the additional mappers. The
     * terminal default never claims a class that lacks the hardcoded
     * fast-path annotations, so there is no value in invoking it
     * here.
     *
     * <p>Three short-circuits keep the per-class cost low for the
     * common case where MP Config sticks with the defaults:
     * <ol>
     *   <li>If no extra annotations are configured beyond the
     *       fast-path defaults, return immediately.</li>
     *   <li>Skip classes already handled by the fast-path observer
     *       so each class flows through the chain at most once.</li>
     *   <li>Match the class against the {@link #extraAnnotations}
     *       set; only classes carrying at least one extra reach the
     *       chain.</li>
     * </ol>
     *
     * @param event       the in-flight {@code ProcessAnnotatedType}
     *                    event; non-{@code null}
     * @param beanManager the in-flight {@code BeanManager}, forwarded
     *                    to every additional mapper invoked here;
     *                    non-{@code null}
     * @param <T>         the annotated type's bean class
     */
    <T> void onProcessOtherAnnotatedType(
            @Observes ProcessAnnotatedType<T> event,
            BeanManager beanManager) {
        if (extraAnnotations.isEmpty()) {
            return;
        }
        Class<T> beanClass = event.getAnnotatedType().getJavaClass();
        if (beanClass.isAnnotationPresent(Singleton.class)
                || beanClass.isAnnotationPresent(Stateless.class)) {
            return;
        }
        if (!classCarriesAnyExtra(beanClass)) {
            return;
        }
        applyChain(event, beanManager, /* runTerminal */ false);
    }

    private <T> void applyChain(ProcessAnnotatedType<T> event,
                                BeanManager beanManager,
                                boolean runTerminal) {
        AnnotatedType<T> annotatedType = event.getAnnotatedType();
        Class<T> beanClass = annotatedType.getJavaClass();

        List<Annotation> result = invokeChain(beanClass, beanManager, runTerminal);
        if (result == null || result.isEmpty()) {
            return;
        }
        if (LOG.isLoggable(Level.DEBUG)) {
            LOG.log(Level.DEBUG,
                    "ejb-module: rewriting AnnotatedType for {0} — before={1} adding={2}",
                    beanClass.getName(),
                    describeExistingAnnotations(annotatedType),
                    describeAdditions(result));
        }
        AnnotatedTypeConfigurator<T> configurator = event.configureAnnotatedType();
        for (Annotation annotation : result) {
            configurator.add(annotation);
        }
    }

    private List<Annotation> invokeChain(Class<?> beanClass,
                                         BeanManager beanManager,
                                         boolean runTerminal) {
        for (EjbAnnotationMapper mapper : additionalMappers) {
            List<Annotation> result = mapper.mapBeanMetadata(beanClass, beanManager);
            if (result != null) {
                return result;
            }
        }
        if (runTerminal && terminalMapper != null) {
            return terminalMapper.mapBeanMetadata(beanClass, beanManager);
        }
        return null;
    }

    private boolean classCarriesAnyExtra(Class<?> beanClass) {
        for (Class<? extends Annotation> annotationType : extraAnnotations) {
            if (beanClass.isAnnotationPresent(annotationType)) {
                return true;
            }
        }
        return false;
    }

    private static String describeExistingAnnotations(AnnotatedType<?> annotatedType) {
        return annotatedType.getAnnotations().stream()
                .map(annotation -> "@" + annotation.annotationType().getSimpleName())
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String describeAdditions(List<Annotation> additions) {
        return additions.stream()
                .map(annotation -> "@" + annotation.annotationType().getSimpleName())
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private void resolveMapperChain() {
        List<EjbAnnotationMapper> candidates = new ArrayList<>();
        for (EjbAnnotationMapper mapper : ServiceLoader.load(EjbAnnotationMapper.class)) {
            candidates.add(mapper);
        }
        if (candidates.isEmpty()) {
            // Hypothetical classpath with ejb-module/api but no
            // ejb-module/impl. invokeChain returns null for every
            // type; addAnnotatedType still surfaces EJB-annotated
            // classes via the stereotype declarations.
            return;
        }
        List<EjbAnnotationMapper> sorted = TestContext
                .loadService(ServicePriorityResolver.class)
                .sort(candidates);
        Iterator<EjbAnnotationMapper> iterator = sorted.iterator();
        while (iterator.hasNext()) {
            EjbAnnotationMapper mapper = iterator.next();
            if (mapper.isAdditionalMapper()) {
                additionalMappers.add(mapper);
            } else if (terminalMapper == null) {
                terminalMapper = mapper;
            }
            // A second terminal mapper would be a misconfiguration —
            // the priority-sorted-first one already won; the rest
            // are ignored. Per the chain contract there should be
            // exactly one terminal default on the classpath.
        }
    }

    private void registerEjbAnnotatedTypes(BeforeBeanDiscovery event, BeanManager beanManager) {
        if (configuredAnnotations.isEmpty()) {
            return;
        }
        Set<Class<?>> candidates = scanClasspathForEjbAnnotatedTypes();
        for (Class<?> beanClass : candidates) {
            AnnotatedType<?> annotatedType = beanManager.createAnnotatedType(beanClass);
            event.addAnnotatedType(annotatedType, "ejb-" + beanClass.getName());
        }
    }

    private Set<Class<?>> scanClasspathForEjbAnnotatedTypes() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Set<Class<?>> matches = new LinkedHashSet<>();
        try {
            List<URL> urls = new UrlSet(classLoader).getUrls();
            AnnotationFinder finder = new AnnotationFinder(new ClasspathArchive(classLoader, urls));
            for (Class<? extends Annotation> annotationType : configuredAnnotations) {
                for (Class<?> ejbClass : finder.findAnnotatedClasses(annotationType)) {
                    if (!isExcluded(ejbClass.getName()) && !hasNormalScopeOrDependent(ejbClass)) {
                        matches.add(ejbClass);
                    }
                }
            }
        } catch (IOException | RuntimeException scanFailure) {
            throw new IllegalStateException(
                    "ejb-module classpath scan for configured bean-defining annotations failed; "
                            + "bootstrap aborted to surface the underlying classpath problem.",
                    scanFailure);
        }
        return matches;
    }

    /**
     * Resolve {@link #BEAN_DEFINING_ANNOTATIONS_KEY} from MP Config
     * (via {@link ConfigResolver}) into a {@code Set} of annotation
     * {@code Class} objects. Defaults ship in this module's
     * {@code META-INF/microprofile-config.properties}; an FQCN the
     * configured value lists but the classloader cannot resolve, or
     * one that resolves to a type that isn't an annotation, fails
     * the bootstrap fast — a quiet skip would silently disable
     * observation of the misconfigured entry.
     */
    private void resolveConfiguredAnnotations() {
        ConfigResolver resolver = TestContext.loadService(ConfigResolver.class);
        List<String> fqcns = resolver.resolve(BEAN_DEFINING_ANNOTATIONS_KEY)
                .map(value -> Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(token -> !token.isEmpty())
                        .toList())
                .orElseGet(List::of);
        if (fqcns.isEmpty()) {
            return;
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Set<Class<? extends Annotation>> resolved = new LinkedHashSet<>();
        for (String fqcn : fqcns) {
            resolved.add(loadAnnotationClass(fqcn, classLoader));
        }
        configuredAnnotations = Set.copyOf(resolved);
        Set<Class<? extends Annotation>> extras = new LinkedHashSet<>(resolved);
        extras.removeAll(FAST_PATH_ANNOTATIONS);
        extraAnnotations = Set.copyOf(extras);
    }

    private static Class<? extends Annotation> loadAnnotationClass(String fqcn, ClassLoader classLoader) {
        Class<?> loaded;
        try {
            loaded = Class.forName(fqcn, false, classLoader);
        } catch (ClassNotFoundException notFound) {
            throw new IllegalStateException(
                    "ejb-module: MP Config key '" + BEAN_DEFINING_ANNOTATIONS_KEY
                            + "' lists FQCN '" + fqcn
                            + "' but the classloader cannot resolve it.",
                    notFound);
        }
        if (!loaded.isAnnotation()) {
            throw new IllegalStateException(
                    "ejb-module: MP Config key '" + BEAN_DEFINING_ANNOTATIONS_KEY
                            + "' lists FQCN '" + fqcn
                            + "' but the resolved class is not an annotation type.");
        }
        @SuppressWarnings("unchecked")
        Class<? extends Annotation> annotationType = (Class<? extends Annotation>) loaded;
        return annotationType;
    }

    private static boolean isExcluded(String className) {
        for (String prefix : SCAN_EXCLUDE_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code beanClass} already carries a bean-defining
     * normal scope or {@code @Dependent}. Such classes are already
     * discoverable under {@code bean-discovery-mode="annotated"} via
     * the standard CDI rules; the extension must skip them when
     * adding annotated types to avoid producing duplicate beans
     * (OpenWebBeans rejects this with
     * {@code DuplicateDefinitionException}).
     *
     * <p>Single pass over the class's annotations; no annotation
     * hierarchy walk. Pseudo-scopes (annotations meta-annotated with
     * {@code @jakarta.inject.Scope} only — for example
     * {@code @jakarta.inject.Singleton}) are intentionally NOT
     * treated as bean-defining here because they aren't bean-defining
     * per the CDI 4.0 spec either.
     */
    private static boolean hasNormalScopeOrDependent(Class<?> beanClass) {
        for (Annotation annotation : beanClass.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (annotationType == Dependent.class
                    || annotationType.isAnnotationPresent(NormalScope.class)) {
                return true;
            }
        }
        return false;
    }
}
