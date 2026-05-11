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
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

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
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;

import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.UrlSet;
import org.apache.xbean.finder.archive.ClasspathArchive;
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

    /** Additional mappers in priority order, populated on {@code BeforeBeanDiscovery}. */
    private final List<EjbAnnotationMapper> additionalMappers = new ArrayList<>();

    /** Terminal default mapper, populated on {@code BeforeBeanDiscovery}. */
    private EjbAnnotationMapper terminalMapper;

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
     * Run the mapper chain against the type being processed and
     * apply the resulting annotations via
     * {@link AnnotatedTypeConfigurator#add(Annotation)}.
     *
     * @param event       the in-flight {@code ProcessAnnotatedType}
     *                    event the CDI runtime delivers;
     *                    non-{@code null}
     * @param beanManager the in-flight {@code BeanManager}, forwarded
     *                    to every mapper in the chain so they have
     *                    access to CDI services; non-{@code null}
     * @param <T>         the annotated type's bean class
     */
    <T> void onProcessAnnotatedType(@Observes ProcessAnnotatedType<T> event, BeanManager beanManager) {
        AnnotatedType<T> annotatedType = event.getAnnotatedType();
        Class<T> beanClass = annotatedType.getJavaClass();

        List<Annotation> result = invokeChain(beanClass, beanManager);
        if (result == null || result.isEmpty()) {
            return;
        }
        AnnotatedTypeConfigurator<T> configurator = event.configureAnnotatedType();
        for (Annotation annotation : result) {
            configurator.add(annotation);
        }
    }

    private List<Annotation> invokeChain(Class<?> beanClass, BeanManager beanManager) {
        for (EjbAnnotationMapper mapper : additionalMappers) {
            List<Annotation> result = mapper.mapBeanMetadata(beanClass, beanManager);
            if (result != null) {
                return result;
            }
        }
        if (terminalMapper != null) {
            return terminalMapper.mapBeanMetadata(beanClass, beanManager);
        }
        return null;
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
        Set<Class<?>> candidates = scanClasspathForEjbAnnotatedTypes();
        for (Class<?> beanClass : candidates) {
            AnnotatedType<?> annotatedType = beanManager.createAnnotatedType(beanClass);
            event.addAnnotatedType(annotatedType, "ejb-" + beanClass.getName());
        }
    }

    private static Set<Class<?>> scanClasspathForEjbAnnotatedTypes() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Set<Class<?>> matches = new LinkedHashSet<>();
        try {
            List<URL> urls = new UrlSet(classLoader).getUrls();
            AnnotationFinder finder = new AnnotationFinder(new ClasspathArchive(classLoader, urls));
            for (Class<?> ejbClass : finder.findAnnotatedClasses(Singleton.class)) {
                if (!isExcluded(ejbClass.getName()) && !hasNormalScopeOrDependent(ejbClass)) {
                    matches.add(ejbClass);
                }
            }
            for (Class<?> ejbClass : finder.findAnnotatedClasses(Stateless.class)) {
                if (!isExcluded(ejbClass.getName()) && !hasNormalScopeOrDependent(ejbClass)) {
                    matches.add(ejbClass);
                }
            }
        } catch (IOException | RuntimeException scanFailure) {
            // The scan is a best-effort discovery aid; surfacing the
            // failure as an opaque CDI bootstrap error would be more
            // disruptive than not running ejb-module on this
            // classpath. The fallback path is the standard CDI
            // discovery — types that already carry a CDI scope still
            // resolve through the existing rules, and the mapper
            // chain still runs against them via ProcessAnnotatedType.
            throw new IllegalStateException(
                    "ejb-module classpath scan for @Singleton / @Stateless types failed; "
                            + "bootstrap aborted to surface the underlying classpath problem.",
                    scanFailure);
        }
        return matches;
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
