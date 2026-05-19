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
package org.os890.jawelte.module.ejb.impl.adapter.contributor;

import java.io.File;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import jakarta.annotation.Priority;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.impl.spi.ArcContextContributor;
import org.os890.jawelte.module.ejb.api.port.EjbAnnotationMapper;

import io.quarkus.arc.processor.BeanProcessor;

/**
 * ejb-module's {@link ArcContextContributor}: replaces the
 * {@code ProcessAnnotatedType} half of the legacy
 * {@code EjbAnnotationExtension} portable CDI Extension.
 *
 * <p>Walks the priority-sorted {@link EjbAnnotationMapper} chain
 * (additional mappers first, lowest {@code @Priority} value wins;
 * the terminal {@code DefaultEjbAnnotationMapper} runs only when
 * every additional mapper returned {@code null}) for every class on
 * {@code target/test-classes} (and {@code target/classes}). The
 * annotations the winning mapper returns are applied to that class
 * via ArC's {@code AnnotationTransformation} surface — same effect
 * the legacy extension achieved through
 * {@code configureAnnotatedType().add(...)}.
 *
 * <p>Adding a CDI scope simultaneously makes the class a managed
 * bean: ArC indexes the class file from {@code target/test-classes}
 * but only registers a bean when the post-transformation annotation
 * set contains a bean-defining annotation. The mapper chain promotes
 * {@code @jakarta.ejb.Singleton} / {@code @jakarta.ejb.Stateless}
 * (and any user-defined EJB annotation a custom mapper claims) into
 * bean-defining shape.
 *
 * <p>Discovered via
 * {@code META-INF/services/org.os890.jawelte.module.cdi.impl.spi.ArcContextContributor}.
 */
public class EjbArcContextContributor implements ArcContextContributor {

    /** No-arg constructor required by {@code ServiceLoader}. */
    public EjbArcContextContributor() {
    }

    @Override
    public void contribute(TestContext testContext, BeanProcessor.Builder builder) {
        Class<?> testClass = testContext.getTestClass();
        List<EjbAnnotationMapper> additionalMappers = new ArrayList<>();
        EjbAnnotationMapper terminalMapper = null;
        for (EjbAnnotationMapper mapper : ServiceLoader.load(
                EjbAnnotationMapper.class, testClass.getClassLoader())) {
            if (mapper.isAdditionalMapper()) {
                additionalMappers.add(mapper);
            } else if (terminalMapper == null
                    || comparePriority(mapper, terminalMapper) < 0) {
                terminalMapper = mapper;
            }
        }
        additionalMappers.sort(
                Comparator.comparingInt(EjbArcContextContributor::priorityValue)
                        .thenComparing(m -> m.getClass().getName()));

        Map<String, List<AnnotationInstance>> precomputed =
                precomputeMapperAdditions(testClass, additionalMappers, terminalMapper);
        if (precomputed.isEmpty()) {
            return;
        }
        builder.addAnnotationTransformation(
                AnnotationTransformation.forClasses()
                        .whenClass(c -> precomputed.containsKey(c.name().toString()))
                        .transform(ctx -> {
                            ClassInfo classInfo = ctx.declaration().asClass();
                            List<AnnotationInstance> additions =
                                    precomputed.get(classInfo.name().toString());
                            if (additions == null) {
                                return;
                            }
                            boolean userScopeAlready = hasUserDeclaredScope(classInfo);
                            boolean transactionalAlready =
                                    classInfo.hasDeclaredAnnotation(JAKARTA_TRANSACTIONAL);
                            for (AnnotationInstance addition : additions) {
                                String fqn = addition.name().toString();
                                if (userScopeAlready && isCdiScopeAnnotation(fqn)) {
                                    // Author-declared CDI scope on the class
                                    // wins — drop the mapper's scope addition.
                                    continue;
                                }
                                if (transactionalAlready
                                        && fqn.equals(JAKARTA_TRANSACTIONAL.toString())) {
                                    // Author-declared @Transactional (with its
                                    // own TxType / rollbackOn) wins.
                                    continue;
                                }
                                ctx.add(addition);
                            }
                        }));
    }

    private static final DotName JAKARTA_TRANSACTIONAL =
            DotName.createSimple("jakarta.transaction.Transactional");

    private static final String TEST_CLASS_SCOPED_FQN =
            "org.os890.jawelte.module.scope.api.TestClassScoped";

    private static int priorityValue(Object mapper) {
        Priority priority = mapper.getClass().getAnnotation(Priority.class);
        return priority == null ? Integer.MAX_VALUE : priority.value();
    }

    private static int comparePriority(EjbAnnotationMapper a, EjbAnnotationMapper b) {
        return Integer.compare(priorityValue(a), priorityValue(b));
    }

    /**
     * For every class on {@code target/test-classes} (and
     * {@code target/classes}), walk the mapper chain and record the
     * additions the winning mapper returned.
     *
     * <p>Mapper chain semantics: {@code null} from every additional
     * mapper hands the class off to the terminal default; an empty
     * list from any mapper claims the class but adds nothing; a
     * non-empty list is the set of annotations to add.
     *
     * <p>{@code @jakarta.ejb.Singleton} and {@code @jakarta.ejb.Stateless}
     * are registered as CDI stereotypes by the legacy
     * {@code EjbAnnotationExtension.BeforeBeanDiscovery} call — so
     * the bean is discoverable even when the mapper chain contributes
     * no scope (empty-list claim or null-everything terminal).
     * Under standalone-ArC the stereotype registration via
     * {@code addStereotype} doesn't take effect, so the contributor
     * mimics the same intent here: when the mapper chain leaves the
     * class without a CDI scope, fall back to the EJB stereotype
     * baseline ({@code @ApplicationScoped} for {@code @Singleton},
     * {@code @Dependent} for {@code @Stateless}).
     */
    private static Map<String, List<AnnotationInstance>> precomputeMapperAdditions(
            Class<?> testClass,
            List<EjbAnnotationMapper> additionalMappers,
            EjbAnnotationMapper terminalMapper) {
        Map<String, List<AnnotationInstance>> result = new LinkedHashMap<>();
        for (Class<?> candidate : scanCandidateClasses(testClass)) {
            List<Annotation> mapped = runMapperChain(candidate, additionalMappers, terminalMapper);
            boolean mapperClaim = mapped != null;
            List<AnnotationInstance> instances = new ArrayList<>();
            if (mapperClaim) {
                for (Annotation annotation : mapped) {
                    instances.add(AnnotationInstance.builder(
                            DotName.createSimple(annotation.annotationType().getName())).build());
                }
            }
            // Stereotype-equivalent fallback for EJB-annotated classes
            // that the mapper chain left without a CDI scope. This
            // keeps the class bean-defining under ArC even when the
            // chain contributed nothing (empty-list claim, null-everything
            // terminal, etc.).
            boolean hasEjbSingleton = candidate.isAnnotationPresent(jakarta.ejb.Singleton.class);
            boolean hasEjbStateless = candidate.isAnnotationPresent(jakarta.ejb.Stateless.class);
            if (hasEjbSingleton || hasEjbStateless) {
                boolean userScope = hasUserDeclaredCdiScopeReflective(candidate);
                boolean mapperScope = instances.stream()
                        .anyMatch(a -> isCdiScopeAnnotation(a.name().toString()));
                if (!userScope && !mapperScope) {
                    DotName fallback = hasEjbSingleton
                            ? DotName.createSimple("jakarta.enterprise.context.ApplicationScoped")
                            : DotName.createSimple("jakarta.enterprise.context.Dependent");
                    instances.add(AnnotationInstance.builder(fallback).build());
                }
            }
            if (instances.isEmpty()) {
                continue;
            }
            result.put(candidate.getName(), instances);
        }
        return result;
    }

    private static boolean hasUserDeclaredCdiScopeReflective(Class<?> candidate) {
        for (Annotation annotation : candidate.getDeclaredAnnotations()) {
            if (isCdiScopeAnnotation(annotation.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }

    private static List<Annotation> runMapperChain(
            Class<?> beanClass,
            List<EjbAnnotationMapper> additionalMappers,
            EjbAnnotationMapper terminalMapper) {
        for (EjbAnnotationMapper mapper : additionalMappers) {
            List<Annotation> result;
            try {
                result = mapper.mapBeanMetadata(beanClass, null);
            } catch (RuntimeException mapperFailure) {
                // The CDI Extension contract says mappers must not
                // throw; a thrown mapper aborts CDI bootstrap. Mirror
                // that — surface the failure.
                throw mapperFailure;
            }
            if (result != null) {
                return result;
            }
        }
        if (terminalMapper == null) {
            return null;
        }
        return terminalMapper.mapBeanMetadata(beanClass, null);
    }

    /**
     * Walks {@code target/test-classes} and {@code target/classes}
     * under the test class's project directory and returns every
     * class loadable through the test class's ClassLoader. Same
     * approach {@code CdiTestBeanContainer.discoverBeanClasses}
     * uses, replicated here to keep ejb-module/impl independent of
     * cdi-module/impl's internal API.
     */
    private static Set<Class<?>> scanCandidateClasses(Class<?> testClass) {
        Set<Class<?>> result = new LinkedHashSet<>();
        ClassLoader cl = testClass.getClassLoader();
        if (cl == null) {
            return result;
        }
        try {
            String resourceName = testClass.getName().replace('.', '/') + ".class";
            URL url = cl.getResource(resourceName);
            if (url == null) {
                return result;
            }
            String path = url.getFile();
            int idx = path.indexOf("target/test-classes");
            if (idx < 0) {
                return result;
            }
            String projectBase = path.substring(0, idx);
            File testClassesDir = new File(projectBase + "target/test-classes");
            if (testClassesDir.isDirectory()) {
                scanDirectory(testClassesDir, testClassesDir, cl, result);
            }
            File classesDir = new File(projectBase + "target/classes");
            if (classesDir.isDirectory()) {
                scanDirectory(classesDir, classesDir, cl, result);
            }
        } catch (RuntimeException scanFailure) {
            // best-effort discovery — any class we miss simply won't
            // get the mapper-chain transformation applied
        }
        return result;
    }

    private static void scanDirectory(File root, File dir, ClassLoader cl, Set<Class<?>> result) {
        File[] entries = dir.listFiles();
        if (entries == null) {
            return;
        }
        for (File entry : entries) {
            if (entry.isDirectory()) {
                scanDirectory(root, entry, cl, result);
                continue;
            }
            if (!entry.getName().endsWith(".class")) {
                continue;
            }
            String relative = entry.getAbsolutePath().substring(root.getAbsolutePath().length() + 1);
            String className = relative.substring(0, relative.length() - ".class".length())
                    .replace(File.separatorChar, '.');
            try {
                result.add(Class.forName(className, false, cl));
            } catch (Throwable loadFailure) {
                // skip classes that fail to load; they cannot host
                // mapper-recognised EJB annotations we care about
            }
        }
    }

    private static boolean hasUserDeclaredScope(ClassInfo classInfo) {
        for (AnnotationInstance annotation : classInfo.declaredAnnotations()) {
            String name = annotation.name().toString();
            if (isCdiScopeAnnotation(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCdiScopeAnnotation(String fqn) {
        return fqn.equals("jakarta.enterprise.context.ApplicationScoped")
                || fqn.equals("jakarta.enterprise.context.RequestScoped")
                || fqn.equals("jakarta.enterprise.context.SessionScoped")
                || fqn.equals("jakarta.enterprise.context.ConversationScoped")
                || fqn.equals("jakarta.enterprise.context.Dependent")
                || fqn.equals("jakarta.inject.Singleton")
                || fqn.equals(TEST_CLASS_SCOPED_FQN)
                || fqn.equals("org.os890.jawelte.module.scope.api.TestMethodScoped");
    }
}
