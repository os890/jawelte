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
package org.os890.jawelte.module.cdi.impl.adapter.container;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.TestBean;
import org.os890.jawelte.core.api.TestBeans;
import org.os890.jawelte.core.api.event.ContainerStarted;
import org.os890.jawelte.core.api.port.BeanScopeMapper;
import org.os890.jawelte.core.api.port.TestBeanContainerPort;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.api.port.WhitelistFilter;
import org.os890.jawelte.module.cdi.impl.adapter.mock.InlineFieldBeanCreator;
import org.os890.jawelte.module.cdi.impl.adapter.mock.MockBeanCreator;
import org.os890.jawelte.module.cdi.impl.spi.ArcContextContributor;
import org.os890.jawelte.module.cdi.impl.util.FrameworkAllowlist;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.ArcInitConfig;
import io.quarkus.arc.ComponentsProvider;
import io.quarkus.arc.processor.AlternativePriorities;
import io.quarkus.arc.processor.BeanArchives;
import io.quarkus.arc.processor.BeanConfigurator;
import io.quarkus.arc.processor.BeanDeployment;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.BeanProcessor;
import io.quarkus.arc.processor.BeanRegistrar;
import io.quarkus.arc.processor.BuildExtension;
import io.quarkus.arc.processor.BuiltinBean;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.arc.processor.ResourceOutput;

/**
 * Quarkus / ArC implementation of {@link TestBeanContainerPort}. Drives
 * the ArC container lifecycle from a regular JUnit extension (jawelte's
 * {@code DelegatingJUnitExtension}) by invoking
 * {@link io.quarkus.arc.processor.BeanProcessor} as a library at test
 * time — the same code path Quarkus's own {@code QuarkusComponentTest}
 * uses internally.
 *
 * <p>Tests do NOT need {@code @QuarkusTest}. Applying
 * {@code @EnableTestBeans} is enough — this container detects which
 * bean classes to discover from the test class's bean archive
 * (classpath scan), applies the {@code @TestBean} alternative set,
 * registers a Mockito-backed mock for every unsatisfied injection
 * point, and boots ArC.
 *
 * <p>No Quarkus build-time extension (no {@code @BuildStep},
 * {@code quarkus-extension.yaml}, or runtime+deployment artifact
 * pair) — everything lives in this single jar.
 *
 * <p>Discovered via {@code ServiceLoader} from
 * {@code META-INF/services/org.os890.jawelte.core.api.port.TestBeanContainerPort}.
 */

public class CdiTestBeanContainer implements TestBeanContainerPort {

    private static final String KEY_OLD_TCCL = "quarkus.oldTccl";
    private static final String KEY_REQUEST_CTRL = "quarkus.requestController";

    private static final String TARGET_TEST_CLASSES = "target/test-classes";
    private static final String TARGET_CLASSES = "target/classes";

    private static final DotName DOT_DEFAULT = DotName.createSimple("jakarta.enterprise.inject.Default");
    private static final DotName DOT_ANY = DotName.createSimple("jakarta.enterprise.inject.Any");
    private static final DotName DOT_NAMED = DotName.createSimple("jakarta.inject.Named");
    private static final DotName DEPENDENT = DotName.createSimple("jakarta.enterprise.context.Dependent");

    /** No-arg constructor required by {@code ServiceLoader}. */
    public CdiTestBeanContainer() {
    }

    @Override
    public void beforeAll(TestContext testContext) {
        Class<?> testClass = testContext.getTestClass();
        EnableTestBeans config = testClass.getAnnotation(EnableTestBeans.class);
        boolean limitToTestBeans = config != null && config.limitToTestBeans();

        Set<Class<?>> selectedAlternatives = collectSelectedAlternatives(testClass);
        Set<InlineField> inlineFields = collectInlineFields(testClass);

        Set<Class<?>> beanClasses = new LinkedHashSet<>();
        beanClasses.add(testClass);
        beanClasses.addAll(selectedAlternatives);

        Set<Class<?>> discoveredClasses = discoverBeanClasses(testClass);
        if (limitToTestBeans) {
            for (InlineField field : inlineFields) {
                beanClasses.add(field.fieldType());
            }
            WhitelistFilter filter = resolveWhitelistFilter();
            if (filter != null) {
                for (Class<?> candidate : discoveredClasses) {
                    if (candidate.equals(testClass)) {
                        continue;
                    }
                    if (candidate.isAnnotationPresent(EnableTestBeans.class)) {
                        continue;
                    }
                    if (hasTestBeanField(candidate)) {
                        continue;
                    }
                    if (filter.isAllowed(candidate)) {
                        beanClasses.add(candidate);
                    }
                }
            }
        } else {
            for (Class<?> candidate : discoveredClasses) {
                if (candidate.equals(testClass)) {
                    continue;
                }
                if (candidate.isAnnotationPresent(EnableTestBeans.class)) {
                    continue;
                }
                if (hasTestBeanField(candidate)) {
                    continue;
                }
                beanClasses.add(candidate);
            }
        }

        List<jakarta.enterprise.inject.spi.Extension> portableExtensions =
                discoverPortableExtensions();
        invokePortableExtensionPhase(
                portableExtensions, jakarta.enterprise.inject.spi.BeforeBeanDiscovery.class);

        List<ArcContextContributor> contextContributors = discoverArcContextContributors();

        ClassLoader oldTccl = buildAndBootArc(
                testClass, beanClasses, selectedAlternatives, inlineFields, limitToTestBeans,
                testContext, contextContributors);
        testContext.bindMetadata(ClassLoader.class, oldTccl);
        testContext.getMetadata(ClassLoader.class)
                .ifPresent(cl -> testContext.bindMetadata(CdiOldTccl.class, new CdiOldTccl(cl)));

        invokePortableExtensionPhase(
                portableExtensions, jakarta.enterprise.inject.spi.AfterDeploymentValidation.class);

        ArcContainer container = Arc.container();
        if (container != null) {
            testContext.bindMetadata(
                    jakarta.enterprise.inject.se.SeContainer.class,
                    new org.os890.jawelte.module.cdi.impl.adapter.se.ArcSeContainerView());
            container.beanManager().getEvent().fire(new ContainerStarted(testClass));
        }
    }

    /**
     * Discover legacy CDI portable extensions via {@code ServiceLoader}.
     * Quarkus's ArC does not natively run them (it only supports
     * {@code BuildCompatibleExtension}), so this container loads them
     * itself and dispatches the lifecycle phases observed by test
     * fixtures (currently {@code BeforeBeanDiscovery} and
     * {@code AfterDeploymentValidation}).
     */
    /**
     * Discover {@link ArcContextContributor} providers via
     * {@code ServiceLoader} and order them by {@code @Priority}
     * ascending so lower numbers run first (consistent with
     * {@code TestModuleLifecyclePort} chain ordering).
     */
    private static List<ArcContextContributor> discoverArcContextContributors() {
        List<ArcContextContributor> result = new ArrayList<>();
        for (ArcContextContributor c : ServiceLoader.load(ArcContextContributor.class)) {
            result.add(c);
        }
        result.sort(Comparator
                .comparingInt(CdiTestBeanContainer::providerPriority)
                .thenComparing(c -> c.getClass().getName()));
        return result;
    }

    private static List<jakarta.enterprise.inject.spi.Extension>
            discoverPortableExtensions() {
        List<jakarta.enterprise.inject.spi.Extension> result = new ArrayList<>();
        for (jakarta.enterprise.inject.spi.Extension ext
                : ServiceLoader.load(jakarta.enterprise.inject.spi.Extension.class)) {
            result.add(ext);
        }
        return result;
    }

    private static void invokePortableExtensionPhase(
            List<jakarta.enterprise.inject.spi.Extension> extensions,
            Class<?> phaseType) {
        if (extensions.isEmpty()) {
            return;
        }
        Object event = java.lang.reflect.Proxy.newProxyInstance(
                phaseType.getClassLoader(),
                new Class<?>[] {phaseType},
                (proxy, method, args) -> {
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) {
                        return Boolean.FALSE;
                    }
                    return null;
                });
        for (jakarta.enterprise.inject.spi.Extension ext : extensions) {
            if (isFrameworkExtension(ext.getClass())) {
                // Skip framework extensions (e.g. SmallRye's
                // ConfigExtension): they expect a full Jakarta SE
                // bootstrap with a live BeanManager, which we cannot
                // provide for a build-time only phase.
                continue;
            }
            for (java.lang.reflect.Method method : ext.getClass().getDeclaredMethods()) {
                java.lang.reflect.Parameter[] params = method.getParameters();
                if (params.length != 1) {
                    continue;
                }
                if (!params[0].isAnnotationPresent(jakarta.enterprise.event.Observes.class)) {
                    continue;
                }
                if (!params[0].getType().equals(phaseType)) {
                    continue;
                }
                method.setAccessible(true);
                try {
                    method.invoke(ext, event);
                } catch (java.lang.reflect.InvocationTargetException ite) {
                    Throwable cause = ite.getTargetException();
                    if (cause instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new IllegalStateException(
                            "Portable extension observer for " + phaseType.getSimpleName()
                                    + " threw " + cause, cause);
                } catch (IllegalAccessException iae) {
                    throw new IllegalStateException(
                            "Cannot invoke portable extension observer "
                                    + ext.getClass().getName() + "#" + method.getName(),
                            iae);
                }
            }
        }
    }

    private static boolean isFrameworkExtension(Class<?> extClass) {
        String name = extClass.getName();
        return name.startsWith("io.smallrye.")
                || name.startsWith("io.quarkus.")
                || name.startsWith("org.jboss.weld.")
                || name.startsWith("org.apache.webbeans.")
                || name.startsWith("org.apache.deltaspike.")
                || name.startsWith("jakarta.")
                || name.startsWith("javax.");
    }

    @Override
    public void postProcessTestInstance(TestContext testContext, Object testInstance) {
        ArcContainer container = Arc.container();
        if (container == null) {
            return;
        }
        injectFields(testInstance, testInstance.getClass(), container);
    }

    @Override
    public void beforeEach(TestContext testContext) {
        ArcContainer container = Arc.container();
        if (container == null) {
            return;
        }
        RequestContextController controller =
                container.select(RequestContextController.class).get();
        controller.activate();
        testContext.bindMetadata(CdiRequestController.class, new CdiRequestController(controller));
    }

    @Override
    public void afterEach(TestContext testContext) {
        testContext.getMetadata(CdiRequestController.class).ifPresent(holder -> {
            holder.controller().deactivate();
            testContext.unbindMetadata(CdiRequestController.class);
        });
    }

    @Override
    public void afterAll(TestContext testContext) {
        Arc.shutdown();
        testContext.getMetadata(CdiOldTccl.class).ifPresent(holder -> {
            Thread.currentThread().setContextClassLoader(holder.classLoader());
            testContext.unbindMetadata(CdiOldTccl.class);
        });
    }

    /**
     * Iterate the test-instance's {@code @Inject} fields (including
     * those declared on superclasses) and resolve each via ArC's
     * programmatic lookup, then set the value reflectively. ArC's
     * type-safe resolution handles the qualifier set; we just hand it
     * the qualifier array we read off the field.
     */
    private static void injectFields(Object testInstance, Class<?> clazz, ArcContainer container) {
        if (clazz == null || clazz == Object.class) {
            return;
        }
        injectFields(testInstance, clazz.getSuperclass(), container);

        for (Field field : clazz.getDeclaredFields()) {
            if (!field.isAnnotationPresent(jakarta.inject.Inject.class)) {
                continue;
            }
            field.setAccessible(true);
            List<Annotation> qualifiers = new ArrayList<>();
            for (Annotation ann : field.getAnnotations()) {
                if (ann.annotationType().isAnnotationPresent(jakarta.inject.Qualifier.class)) {
                    qualifiers.add(ann);
                }
            }
            try {
                Annotation[] qArr = qualifiers.isEmpty()
                        ? new Annotation[] {jakarta.enterprise.inject.Default.Literal.INSTANCE}
                        : qualifiers.toArray(new Annotation[0]);
                Object value = resolveInjectionValue(container, field, qArr);
                field.set(testInstance, value);
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new IllegalStateException(
                        "Failed to inject field " + clazz.getName() + "." + field.getName(), e);
            }
        }
    }

    /**
     * Resolve the value to assign to a single {@code @Inject} field.
     * Provider/Instance/List wrapper fields are unwrapped: ArC's
     * built-in wrapper machinery serves the wrapped bean, but we
     * have to hand the field an instance of the wrapper, not the
     * wrapped value. Plain fields fall back to a raw-type lookup.
     */
    private static Object resolveInjectionValue(
            ArcContainer container, Field field, Annotation[] qArr) {
        Class<?> fieldType = field.getType();
        if (fieldType == jakarta.enterprise.inject.Instance.class
                || fieldType == jakarta.inject.Provider.class) {
            Class<?> inner = extractFirstTypeArgument(field);
            return container.beanManager().createInstance().select(inner, qArr);
        }
        return container.instance(fieldType, qArr).get();
    }

    private static Class<?> extractFirstTypeArgument(Field field) {
        java.lang.reflect.Type generic = field.getGenericType();
        if (generic instanceof java.lang.reflect.ParameterizedType pt) {
            java.lang.reflect.Type[] args = pt.getActualTypeArguments();
            if (args.length >= 1) {
                java.lang.reflect.Type arg = args[0];
                if (arg instanceof Class<?> c) {
                    return c;
                }
                if (arg instanceof java.lang.reflect.ParameterizedType inner) {
                    return (Class<?>) inner.getRawType();
                }
            }
        }
        return Object.class;
    }

    // ----- ArC build + boot ----------------------------------------------

    private ClassLoader buildAndBootArc(
            Class<?> testClass,
            Set<Class<?>> beanClasses,
            Set<Class<?>> selectedAlternatives,
            Set<InlineField> inlineFields,
            boolean limitToTestBeans,
            TestContext testContext,
            List<ArcContextContributor> contextContributors) {
        Arc.shutdown();

        ClassLoader oldTccl = Thread.currentThread().getContextClassLoader();
        try {
            Index rawIndex = indexClasses(beanClasses);
            IndexView immutableIndex = BeanArchives.buildImmutableBeanArchiveIndex(rawIndex);
            IndexView computingIndex = BeanArchives.buildComputingBeanArchiveIndex(
                    oldTccl, new ConcurrentHashMap<>(), immutableIndex);

            File testOutputDirectory = resolveTestOutputDirectory(testClass);
            File generatedSourcesDir = new File(testOutputDirectory.getParentFile(), "generated-arc-sources");
            File componentsProviderFile = new File(
                    generatedSourcesDir + "/" + testClass.getPackageName().replace('.', '/'),
                    ComponentsProvider.class.getSimpleName());

            Set<DotName> altDotNames = new HashSet<>();
            for (Class<?> alt : selectedAlternatives) {
                altDotNames.add(DotName.createSimple(alt.getName()));
            }

            BeanProcessor.Builder builder = BeanProcessor.builder()
                    .setName(testClass.getName().replace('.', '_'))
                    .setImmutableBeanArchiveIndex(immutableIndex)
                    .setComputingBeanArchiveIndex(computingIndex);

            // Force the test class to be a CDI bean. Without an
            // explicit scope annotation, ArC's default (annotated)
            // discovery mode skips the test class — and skipping it
            // means its @Inject fields are never collected as
            // injection points, so the auto-mock BeanRegistrar can't
            // see them. @Dependent is the natural choice for a test
            // class: one instance per @Inject site (CDI default for
            // managed beans without an explicit scope).
            DotName testClassDotName = DotName.createSimple(testClass.getName());
            builder.addAnnotationTransformation(
                    AnnotationTransformation.forClasses()
                            .whenClass(c -> c.name().equals(testClassDotName))
                            .transform(ctx -> {
                                if (!hasScope(ctx)) {
                                    ctx.add(AnnotationInstance.builder(DEPENDENT).build());
                                }
                            }));

            // BeanScopeMapper-driven class-level scope rewrites. For
            // every type carrying a registered trigger annotation
            // (e.g. @ConfigBean, @SessionScoped), replace any direct
            // scope annotation with the mapper's target scope so the
            // ArC bean is built with the right scope. Mappers that
            // opt into preserveExplicitDirectScopes() honour an
            // explicit user-declared direct scope that differs from
            // both the trigger and the trigger's stereotype-
            // contributed scope.
            List<BeanScopeMapper> scopeMappers = discoverBeanScopeMappers();
            for (BeanScopeMapper mapper : scopeMappers) {
                DotName triggerDot = DotName.createSimple(mapper.trigger().getName());
                DotName targetDot = DotName.createSimple(mapper.targetScope().getName());
                boolean preserveExplicit = mapper.preserveExplicitDirectScopes();
                builder.addAnnotationTransformation(
                        AnnotationTransformation.forClasses()
                                .whenClass(c -> c.hasDeclaredAnnotation(triggerDot)
                                        && !c.name().equals(testClassDotName))
                                .transform(ctx -> {
                                    if (preserveExplicit && hasExplicitOtherScope(ctx, triggerDot)) {
                                        return;
                                    }
                                    // Remove every direct CDI scope
                                    // annotation, including the trigger
                                    // itself when it IS a scope (e.g.
                                    // @SessionScoped). The target scope
                                    // is then added as a direct annotation
                                    // and wins per CDI's class-level-
                                    // scope-wins rule.
                                    ctx.remove(ann -> isCdiScopeAnnotation(ann.name()));
                                    ctx.add(AnnotationInstance.builder(targetDot).build());
                                }));
            }

            // For classes named in @TestBean(bean=...): if the class
            // is already @Alternative, add @Dependent so it qualifies
            // as a managed bean under CDI's annotated discovery
            // (@Alternative alone is not a bean-defining annotation
            // per CDI 4.0 §3.1.1). Non-@Alternative classes
            // (scenario-35) are not transformed — ArC will silently
            // ignore them. Priorities are assigned via
            // AlternativePriorities below, so we do not touch the
            // existing @Priority either.
            if (!altDotNames.isEmpty()) {
                builder.addAnnotationTransformation(
                        AnnotationTransformation.forClasses()
                                .whenClass(c -> altDotNames.contains(c.name())
                                        && c.hasAnnotation(DotNames.ALTERNATIVE))
                                .transform(ctx -> {
                                    if (!hasScope(ctx)) {
                                        ctx.add(AnnotationInstance.builder(DEPENDENT).build());
                                    }
                                }));
                AlternativePriorities altPriorities = (target, stereotypes) -> {
                    if (target.kind() != org.jboss.jandex.AnnotationTarget.Kind.CLASS) {
                        return null;
                    }
                    return altDotNames.contains(target.asClass().name())
                            ? Integer.MAX_VALUE
                            : null;
                };
                builder.setAlternativePriorities(altPriorities);
            }

            if (!selectedAlternatives.isEmpty() && !limitToTestBeans) {
                builder.addExcludeType(classInfo -> {
                    if (!classInfo.hasAnnotation(DotNames.ALTERNATIVE)) {
                        return false;
                    }
                    if (altDotNames.contains(classInfo.name())) {
                        return false;
                    }
                    if (classInfo.hasAnnotation(DotNames.PRIORITY)) {
                        return false;
                    }
                    return hasTypeClash(classInfo, selectedAlternatives, computingIndex);
                });
            }

            Class<? extends Annotation> autoMockDefaultScope = readAutoMockDefaultScope();
            builder.addBeanRegistrar(new MockAndInlineBeanRegistrar(
                    inlineFields, computingIndex, scopeMappers, autoMockDefaultScope));
            builder.setRemoveUnusedBeans(false);
            builder.setOutput(new GeneratedResourceOutput(testOutputDirectory, componentsProviderFile));

            for (ArcContextContributor contributor : contextContributors) {
                contributor.contribute(testContext, builder);
            }

            BeanProcessor processor = builder.build();
            processor.process();

            ArcTestClassLoader testClassLoader = new ArcTestClassLoader(oldTccl, componentsProviderFile);
            Thread.currentThread().setContextClassLoader(testClassLoader);
            Arc.initialize(ArcInitConfig.builder().setTestMode(true).build());
        } catch (Exception e) {
            Thread.currentThread().setContextClassLoader(oldTccl);
            throw new IllegalStateException("Failed to bootstrap ArC for " + testClass.getName(), e);
        }
        return oldTccl;
    }

    /**
     * Boot ArC for an {@code SeContainerInitializer}-style external
     * bootstrap. Used by tests that opt out of jawelte's
     * managed-container lifecycle via
     * {@code @EnableTestBeans(manageContainer = false)} and call
     * {@code SeContainerInitializer.newInstance().initialize()} from
     * a {@code @BeforeAll} themselves.
     *
     * <p>Scans the current working directory's
     * {@code target/classes} and {@code target/test-classes} for bean
     * candidates, builds a Jandex index, runs the same
     * {@code BeanProcessor} pipeline as the test-driven path (minus
     * the test-class-specific annotation transformations), and finally
     * calls {@code Arc.initialize}. Idempotent: a second call shuts
     * the previous container down first.
     */
    public static void bootArcForSeShim() {
        Arc.shutdown();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = CdiTestBeanContainer.class.getClassLoader();
        }
        try {
            Set<Class<?>> beanClasses = discoverBeanClassesFromCwd(cl);
            Index rawIndex = indexClasses(beanClasses);
            IndexView immutableIndex = BeanArchives.buildImmutableBeanArchiveIndex(rawIndex);
            IndexView computingIndex = BeanArchives.buildComputingBeanArchiveIndex(
                    cl, new ConcurrentHashMap<>(), immutableIndex);

            File outputDir = new File("target/test-classes");
            File generatedSourcesDir = new File("target/generated-arc-sources");
            File componentsProviderFile = new File(
                    generatedSourcesDir, ComponentsProvider.class.getSimpleName());

            BeanProcessor.Builder builder = BeanProcessor.builder()
                    .setName("jawelte_se_shim")
                    .setImmutableBeanArchiveIndex(immutableIndex)
                    .setComputingBeanArchiveIndex(computingIndex);
            builder.setRemoveUnusedBeans(false);
            builder.setOutput(new GeneratedResourceOutput(outputDir, componentsProviderFile));

            BeanProcessor processor = builder.build();
            processor.process();

            ArcTestClassLoader testClassLoader = new ArcTestClassLoader(cl, componentsProviderFile);
            Thread.currentThread().setContextClassLoader(testClassLoader);
            Arc.initialize(ArcInitConfig.builder().setTestMode(true).build());
        } catch (Exception e) {
            String causeMessage = e.getMessage() != null
                    ? e.getMessage()
                    : e.getClass().getSimpleName();
            throw new IllegalStateException(
                    "Failed to bootstrap ArC via SeContainerInitializer: " + causeMessage, e);
        }
    }

    private static Set<Class<?>> discoverBeanClassesFromCwd(ClassLoader cl) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        File testClassesDir = new File(TARGET_TEST_CLASSES);
        if (testClassesDir.isDirectory()) {
            scanDirectory(testClassesDir, testClassesDir, cl, classes);
        }
        File classesDir = new File(TARGET_CLASSES);
        if (classesDir.isDirectory()) {
            scanDirectory(classesDir, classesDir, cl, classes);
        }
        scanClasspathArchives(cl, classes);
        return classes;
    }

    private static File resolveTestOutputDirectory(Class<?> testClass) {
        String testClassResource = testClass.getName().replace('.', '/') + ".class";
        URL testClassUrl = testClass.getClassLoader().getResource(testClassResource);
        if (testClassUrl != null) {
            try {
                String testClassPath = new File(testClassUrl.toURI()).getAbsolutePath();
                int targetIdx = testClassPath.indexOf(TARGET_TEST_CLASSES);
                if (targetIdx > 0) {
                    return new File(testClassPath.substring(0, targetIdx) + TARGET_TEST_CLASSES);
                }
            } catch (URISyntaxException e) {
                // fall through
            }
        }
        return new File(TARGET_TEST_CLASSES);
    }

    private static Index indexClasses(Set<Class<?>> classes) throws IOException {
        Indexer indexer = new Indexer();
        Set<Class<?>> visited = new HashSet<>();
        for (Class<?> clazz : classes) {
            indexClass(indexer, clazz, visited);
        }
        return indexer.complete();
    }

    private static void indexClass(
            Indexer indexer, Class<?> clazz, Set<Class<?>> visited) throws IOException {
        if (clazz == null || clazz == Object.class || !visited.add(clazz)) {
            return;
        }
        String resourceName = clazz.getName().replace('.', '/') + ".class";
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = CdiTestBeanContainer.class.getClassLoader();
        }
        try (InputStream stream = cl.getResourceAsStream(resourceName)) {
            if (stream != null) {
                indexer.index(stream);
            }
        }
        if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
            indexClass(indexer, clazz.getSuperclass(), visited);
        }
        for (Class<?> iface : clazz.getInterfaces()) {
            indexClass(indexer, iface, visited);
        }
        // Index annotation classes so ArC's annotated discovery can
        // see meta-annotations (e.g. @Stereotype, @NormalScope) that
        // turn user annotations into bean-defining annotations.
        for (Annotation ann : clazz.getAnnotations()) {
            indexClass(indexer, ann.annotationType(), visited);
        }
    }

    // ----- TestBean collection ------------------------------------------

    private static Set<Class<?>> collectSelectedAlternatives(Class<?> testClass) {
        Set<Class<?>> selected = new LinkedHashSet<>();
        Set<Class<? extends Annotation>> visited = new LinkedHashSet<>();
        collectTestBeans(testClass.getAnnotations(), selected, visited);
        return selected;
    }

    private static void collectTestBeans(
            Annotation[] annotations,
            Set<Class<?>> selected,
            Set<Class<? extends Annotation>> visited) {
        for (Annotation ann : annotations) {
            Class<? extends Annotation> annType = ann.annotationType();
            if (ann instanceof TestBean tb) {
                validateTestBeanShape(tb);
                addClassIfNotVoid(tb.bean(), selected);
                addClassIfNotVoid(tb.beanProducer(), selected);
            } else if (ann instanceof TestBeans tbs) {
                for (TestBean tb : tbs.value()) {
                    validateTestBeanShape(tb);
                    addClassIfNotVoid(tb.bean(), selected);
                    addClassIfNotVoid(tb.beanProducer(), selected);
                }
            }
            if (!visited.contains(annType)
                    && !annType.getName().startsWith("java.")
                    && !annType.getName().startsWith("jakarta.")) {
                visited.add(annType);
                collectTestBeans(annType.getAnnotations(), selected, visited);
            }
        }
    }

    private static void validateTestBeanShape(TestBean tb) {
        if (tb.bean() != void.class && tb.beanProducer() != void.class) {
            throw new IllegalStateException(
                    "@TestBean must set bean OR beanProducer, not both");
        }
    }

    private static void addClassIfNotVoid(Class<?> candidate, Set<Class<?>> selected) {
        if (candidate != null && candidate != void.class) {
            selected.add(candidate);
        }
    }

    // ----- Inline @TestBean fields --------------------------------------

    private static Set<InlineField> collectInlineFields(Class<?> testClass) {
        Set<InlineField> fields = new LinkedHashSet<>();
        for (Field field : testClass.getDeclaredFields()) {
            if (!field.isAnnotationPresent(TestBean.class)) {
                continue;
            }
            if (!Modifier.isStatic(field.getModifiers())) {
                throw new IllegalStateException(
                        "@TestBean field must be static: "
                                + testClass.getName() + "." + field.getName());
            }
            field.setAccessible(true);
            Object currentValue;
            try {
                currentValue = field.get(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "Failed to read @TestBean field "
                                + testClass.getName() + "." + field.getName(), e);
            }
            if (currentValue == null) {
                throw new IllegalStateException(
                        "@TestBean static field "
                                + testClass.getName() + "." + field.getName() + " is null");
            }
            Set<Annotation> qualifiers = new LinkedHashSet<>();
            Class<? extends Annotation> fieldScope = null;
            for (Annotation ann : field.getAnnotations()) {
                Class<? extends Annotation> annType = ann.annotationType();
                if (annType.isAnnotationPresent(jakarta.inject.Qualifier.class)) {
                    qualifiers.add(ann);
                }
                if (annType.isAnnotationPresent(jakarta.enterprise.context.NormalScope.class)
                        || annType.isAnnotationPresent(jakarta.inject.Scope.class)) {
                    fieldScope = annType;
                }
            }
            qualifiers.removeIf(a -> a.annotationType() == TestBean.class);
            fields.add(new InlineField(
                    testClass, field.getName(), field.getType(), qualifiers, fieldScope));
        }
        return fields;
    }

    private static boolean hasTestBeanField(Class<?> cls) {
        for (Field field : cls.getDeclaredFields()) {
            if (field.isAnnotationPresent(TestBean.class)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Discover registered {@link BeanScopeMapper}s via
     * {@code ServiceLoader} and order them deterministically by
     * trigger-annotation FQN. Used to drive class-level scope rewrites
     * at {@code BeanProcessor} build time and to default the scope of
     * {@code @TestBean}-derived synthetic beans.
     */
    private static List<BeanScopeMapper> discoverBeanScopeMappers() {
        List<BeanScopeMapper> result = new ArrayList<>();
        for (BeanScopeMapper m : ServiceLoader.load(BeanScopeMapper.class)) {
            result.add(m);
        }
        result.sort(Comparator.comparing(m -> m.trigger().getName()));
        return result;
    }

    /**
     * Read the MP Config key
     * {@code org.os890.jawelte.module.cdi.auto-mock.default-scope}
     * and resolve it to an {@code Annotation} class. {@code null}
     * when the key is unset or the configured class is unloadable
     * — callers fall back to the JDK-vs-user-type default heuristic.
     */
    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> readAutoMockDefaultScope() {
        try {
            org.eclipse.microprofile.config.Config cfg =
                    org.eclipse.microprofile.config.ConfigProvider.getConfig();
            return cfg.getOptionalValue(
                            "org.os890.jawelte.module.cdi.auto-mock.default-scope", String.class)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(name -> {
                        try {
                            Class<?> klass = Class.forName(name, false,
                                    Thread.currentThread().getContextClassLoader());
                            if (Annotation.class.isAssignableFrom(klass)) {
                                return (Class<? extends Annotation>) klass;
                            }
                            return (Class<? extends Annotation>) null;
                        } catch (ClassNotFoundException missing) {
                            return null;
                        }
                    })
                    .orElse(null);
        } catch (RuntimeException missingMpConfig) {
            return null;
        }
    }

    /**
     * Pick the active {@link WhitelistFilter} via {@code ServiceLoader},
     * preferring lower {@code @Priority} values. Returns a default
     * implementation (framework-allowlist only) when no SPI provider is
     * registered — so {@code @EnableTestBeans(limitToTestBeans = true)}
     * always has a usable filter and the bundled framework prefixes
     * remain available to user code.
     */
    private static WhitelistFilter resolveWhitelistFilter() {
        List<WhitelistFilter> providers = new ArrayList<>();
        for (WhitelistFilter provider : ServiceLoader.load(WhitelistFilter.class)) {
            providers.add(provider);
        }
        if (providers.isEmpty()) {
            return FrameworkAllowlist::isAllowlisted;
        }
        providers.sort(Comparator
                .comparingInt(CdiTestBeanContainer::providerPriority)
                .thenComparing(p -> p.getClass().getName()));
        return providers.get(0);
    }

    private static int providerPriority(Object provider) {
        jakarta.annotation.Priority p = provider.getClass()
                .getAnnotation(jakarta.annotation.Priority.class);
        return p != null ? p.value() : Integer.MAX_VALUE;
    }

    // ----- Bean discovery (classpath scan) ------------------------------

    private static Set<Class<?>> discoverBeanClasses(Class<?> testClass) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        ClassLoader cl = testClass.getClassLoader();
        try {
            String testClassResource = testClass.getName().replace('.', '/') + ".class";
            URL url = cl.getResource(testClassResource);
            if (url != null) {
                String path = url.getFile();
                int idx = path.indexOf(TARGET_TEST_CLASSES);
                if (idx >= 0) {
                    String projectBase = path.substring(0, idx);
                    File testClassesDir = new File(projectBase + TARGET_TEST_CLASSES);
                    if (testClassesDir.isDirectory()) {
                        scanDirectory(testClassesDir, testClassesDir, cl, classes);
                    }
                    File classesDir = new File(projectBase + TARGET_CLASSES);
                    if (classesDir.isDirectory()) {
                        scanDirectory(classesDir, classesDir, cl, classes);
                    }
                }
            }
            scanClasspathArchives(cl, classes);
        } catch (Exception e) {
            // Discovery is best-effort. Anything missed shows up later
            // as an unsatisfied injection point or an explicit
            // registration error.
        }
        return classes;
    }

    private static void scanClasspathArchives(ClassLoader cl, Set<Class<?>> classes) {
        try {
            Enumeration<URL> beansXmls = cl.getResources("META-INF/beans.xml");
            Set<String> scanned = new HashSet<>();
            while (beansXmls.hasMoreElements()) {
                URL beansUrl = beansXmls.nextElement();
                if ("jar".equals(beansUrl.getProtocol())) {
                    scanJarArchive(beansUrl, cl, classes, scanned);
                } else if ("file".equals(beansUrl.getProtocol())) {
                    scanFileArchive(beansUrl, cl, classes, scanned);
                }
            }
        } catch (IOException e) {
            // best-effort; ignore
        }
    }

    private static void scanJarArchive(
            URL beansUrl, ClassLoader cl, Set<Class<?>> classes, Set<String> scanned) {
        try {
            java.net.JarURLConnection conn = (java.net.JarURLConnection) beansUrl.openConnection();
            String jarPath = conn.getJarFileURL().toString();
            if (!scanned.add(jarPath)) {
                return;
            }
            try (JarFile jar = conn.getJarFile()) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.endsWith(".class")) {
                        continue;
                    }
                    if (isVendorOrJdkPath(name)) {
                        continue;
                    }
                    String className = name.replace('/', '.').replace(".class", "");
                    if (isAnonymousOrLocal(className)) {
                        continue;
                    }
                    try {
                        classes.add(cl.loadClass(className));
                    } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                        // Skip missing transitive deps.
                    }
                }
            }
        } catch (IOException ignored) {
            // best-effort; ignore
        }
    }

    private static void scanFileArchive(
            URL beansUrl, ClassLoader cl, Set<Class<?>> classes, Set<String> scanned) {
        try {
            File beansFile = new File(beansUrl.toURI());
            File rootDir = beansFile.getParentFile().getParentFile();
            if (rootDir == null || !rootDir.isDirectory()) {
                return;
            }
            String rootPath = rootDir.getAbsolutePath();
            if (!scanned.add(rootPath)) {
                return;
            }
            scanDirectory(rootDir, rootDir, cl, classes);
        } catch (URISyntaxException ignored) {
            // best-effort; ignore
        }
    }

    private static boolean isVendorOrJdkPath(String classFilePath) {
        return classFilePath.startsWith("java/")
                || classFilePath.startsWith("javax/")
                || classFilePath.startsWith("jakarta/")
                || classFilePath.startsWith("io/quarkus/arc/")
                || classFilePath.startsWith("org/jboss/weld/")
                || classFilePath.startsWith("org/apache/webbeans/")
                || classFilePath.startsWith("org/os890/jawelte/module/cdi/impl/")
                || classFilePath.startsWith("META-INF/");
    }

    private static final int MAX_SCAN_DEPTH = 50;

    private static void scanDirectory(File root, File dir, ClassLoader cl, Set<Class<?>> classes) {
        scanDirectory(root, dir, cl, classes, 0);
    }

    private static void scanDirectory(
            File root, File dir, ClassLoader cl, Set<Class<?>> classes, int depth) {
        if (depth > MAX_SCAN_DEPTH) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(root, file, cl, classes, depth + 1);
            } else if (file.getName().endsWith(".class")) {
                String rel = root.toURI().relativize(file.toURI()).getPath();
                String className = rel.replace('/', '.').replace(".class", "");
                if (className.startsWith("META-INF") || isAnonymousOrLocal(className)) {
                    continue;
                }
                try {
                    classes.add(cl.loadClass(className));
                } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                    // Skip; transitive class may be unloadable here.
                }
            }
        }
    }

    /**
     * Whether the given binary class name corresponds to an anonymous
     * or local class — {@code Outer$1}, {@code Outer$2$3}, etc. Named
     * static-nested or inner classes ({@code Outer$Inner}) are kept so
     * user beans declared inside a test class are discoverable.
     */
    private static boolean isAnonymousOrLocal(String binaryName) {
        int dollar = binaryName.lastIndexOf('$');
        if (dollar < 0) {
            return false;
        }
        String suffix = binaryName.substring(dollar + 1);
        return suffix.isEmpty() || Character.isDigit(suffix.charAt(0));
    }

    // ----- Scope / type utilities --------------------------------------

    private static boolean hasScope(AnnotationTransformation.TransformationContext ctx) {
        return ctx.hasAnnotation(DotNames.SINGLETON)
                || ctx.hasAnnotation(DEPENDENT)
                || ctx.hasAnnotation(DotNames.APPLICATION_SCOPED)
                || ctx.hasAnnotation(DotName.createSimple(RequestScoped.class.getName()));
    }

    /**
     * Whether the transformation target has a directly-declared CDI
     * scope annotation other than {@code triggerDot}. Used by the
     * {@code BeanScopeMapper}-driven class-level remap to honour
     * mappers that opt into preserving an explicit user-declared
     * scope.
     */
    private static boolean hasExplicitOtherScope(
            AnnotationTransformation.TransformationContext ctx, DotName triggerDot) {
        for (AnnotationInstance ann : ctx.annotations()) {
            DotName n = ann.name();
            if (n.equals(triggerDot)) {
                continue;
            }
            if (isCdiScopeAnnotation(n)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the given annotation FQN names a CDI scope known to
     * cdi-module. Limited to the CDI-built-in scopes plus jawelte's
     * own test-lifecycle scopes (looked up reflectively to avoid a
     * compile-time dependency on scope-module).
     */
    private static boolean isCdiScopeAnnotation(DotName name) {
        String fqn = name.toString();
        return fqn.equals("jakarta.enterprise.context.ApplicationScoped")
                || fqn.equals("jakarta.enterprise.context.RequestScoped")
                || fqn.equals("jakarta.enterprise.context.SessionScoped")
                || fqn.equals("jakarta.enterprise.context.ConversationScoped")
                || fqn.equals("jakarta.enterprise.context.Dependent")
                || fqn.equals("jakarta.inject.Singleton")
                || fqn.equals("org.os890.jawelte.module.scope.api.TestClassScoped")
                || fqn.equals("org.os890.jawelte.module.scope.api.TestMethodScoped");
    }

    private static boolean hasTypeClash(
            ClassInfo altClass, Set<Class<?>> selected, IndexView index) {
        Set<DotName> altTypes = collectBeanTypes(altClass);
        for (Class<?> sel : selected) {
            ClassInfo selInfo = index.getClassByName(DotName.createSimple(sel.getName()));
            if (selInfo == null) {
                continue;
            }
            Set<DotName> selTypes = collectBeanTypes(selInfo);
            for (DotName t : altTypes) {
                if (!t.equals(DotNames.OBJECT) && selTypes.contains(t)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<DotName> collectBeanTypes(ClassInfo classInfo) {
        Set<DotName> types = new HashSet<>();
        types.add(classInfo.name());
        types.addAll(classInfo.interfaceNames());
        if (classInfo.superName() != null) {
            types.add(classInfo.superName());
        }
        types.add(DotNames.OBJECT);
        return types;
    }

    // ----- Mock / inline-field BeanRegistrar ----------------------------

    private static class MockAndInlineBeanRegistrar implements BeanRegistrar {

        private final Set<InlineField> inlineFields;
        private final IndexView index;
        private final List<BeanScopeMapper> scopeMappers;
        private final Class<? extends Annotation> autoMockDefaultScope;

        MockAndInlineBeanRegistrar(
                Set<InlineField> inlineFields,
                IndexView index,
                List<BeanScopeMapper> scopeMappers,
                Class<? extends Annotation> autoMockDefaultScope) {
            this.inlineFields = inlineFields;
            this.index = index;
            this.scopeMappers = scopeMappers;
            this.autoMockDefaultScope = autoMockDefaultScope;
        }

        @Override
        public void register(RegistrationContext registrationContext) {
            Set<DotName> inlineFieldTypes = registerInlineFieldBeans(registrationContext);
            Map<DotName, Set<String>> nonbindingMembers =
                    readNonbindingMembers(registrationContext);
            registerMocksForUnsatisfiedInjectionPoints(
                    registrationContext, inlineFieldTypes, nonbindingMembers);
        }

        private Class<? extends Annotation> mappedScopeFor(
                Class<? extends Annotation> trigger, Class<? extends Annotation> fallback) {
            for (BeanScopeMapper mapper : scopeMappers) {
                if (mapper.trigger().equals(trigger)) {
                    return mapper.targetScope();
                }
            }
            return fallback;
        }

        private Set<DotName> registerInlineFieldBeans(RegistrationContext registrationContext) {
            Set<DotName> inlineFieldTypes = new HashSet<>();
            for (InlineField field : inlineFields) {
                DotName typeName = DotName.createSimple(field.fieldType().getName());
                Class<? extends Annotation> scope = field.scope() != null
                        ? field.scope()
                        : mappedScopeFor(TestBean.class, Singleton.class);
                BeanConfigurator<Object> configurator = registrationContext
                        .configure(typeName)
                        .scope(scope)
                        .addType(ClassType.create(typeName))
                        .defaultBean()
                        .creator(InlineFieldBeanCreator.class)
                        .param("declaringClass", field.declaringClass().getName())
                        .param("fieldName", field.fieldName());
                for (Annotation q : field.qualifiers()) {
                    configurator.addQualifier(
                            AnnotationInstance.builder(
                                    DotName.createSimple(q.annotationType().getName())).build());
                }
                if (field.qualifiers().isEmpty()) {
                    configurator.addQualifier(
                            AnnotationInstance.builder(DotNames.DEFAULT).build());
                }
                configurator.done();
                inlineFieldTypes.add(typeName);
            }
            return inlineFieldTypes;
        }

        private static Map<DotName, Set<String>> readNonbindingMembers(
                RegistrationContext registrationContext) {
            BeanDeployment deployment = registrationContext.get(BuildExtension.Key.DEPLOYMENT);
            Map<DotName, Set<String>> result = new HashMap<>();
            for (ClassInfo qualifier : deployment.getQualifiers()) {
                Set<String> nb = new HashSet<>();
                for (MethodInfo m : qualifier.methods()) {
                    if (m.hasAnnotation(DotNames.NONBINDING)) {
                        nb.add(m.name());
                    }
                }
                nb.addAll(deployment.getQualifierNonbindingMembers(qualifier.name()));
                if (!nb.isEmpty()) {
                    result.put(qualifier.name(), nb);
                }
            }
            return result;
        }

        private void registerMocksForUnsatisfiedInjectionPoints(
                RegistrationContext registrationContext,
                Set<DotName> inlineFieldTypes,
                Map<DotName, Set<String>> nonbindingMembers) {
            List<BeanInfo> beans = registrationContext.beans().collect();
            Set<String> registeredMockKeys = new HashSet<>();
            for (InjectionPointInfo ip : registrationContext.getInjectionPoints()) {
                BuiltinBean builtin = BuiltinBean.resolve(ip);
                if (builtin != null
                        && builtin != BuiltinBean.INSTANCE) {
                    // BeanManager / Event / InjectionPoint / @All List
                    // / etc. are served by ArC's own built-ins. Only
                    // Provider/Instance (BuiltinBean.INSTANCE) needs
                    // an auto-mock for the wrapped target type — and
                    // ArC has already unwrapped Provider/Instance for
                    // us, so ip.getRequiredType() returns the wrapped
                    // type directly.
                    continue;
                }
                Type effectiveType = ip.getRequiredType();
                Set<AnnotationInstance> requiredQualifiers = ip.getRequiredQualifiers();

                if (isBuiltInType(effectiveType, requiredQualifiers)) {
                    continue;
                }
                if (inlineFieldTypes.contains(effectiveType.name())) {
                    continue;
                }
                if (isSatisfied(effectiveType, requiredQualifiers, beans)) {
                    continue;
                }
                if (hasSyntheticBeanBinding(effectiveType.name())) {
                    // The type is bound to a DeltaSpike partial-bean
                    // proxy producer (directly or via a meta-annotation
                    // tree). Skip auto-mock so the user-supplied
                    // producer remains responsible for satisfying the
                    // IP; if none is on the classpath the deployment
                    // validation legitimately fails.
                    continue;
                }
                String mockKey = buildMockKey(effectiveType, requiredQualifiers, nonbindingMembers);
                if (!registeredMockKeys.add(mockKey)) {
                    continue;
                }
                Set<AnnotationInstance> cleanedQualifiers =
                        stripNonbindingValues(requiredQualifiers, nonbindingMembers);
                String typeName = effectiveType.name().toString();
                Class<? extends Annotation> scope;
                if (isJdkType(typeName)) {
                    scope = Dependent.class;
                } else if (autoMockDefaultScope != null) {
                    scope = autoMockDefaultScope;
                } else {
                    scope = RequestScoped.class;
                }

                // Add BOTH the parameterized type AND the raw type
                // as bean types. The parameterized one satisfies the
                // build-time IP resolution; the raw one allows raw
                // reflection lookups (container.instance(Class, ...))
                // to find the bean at runtime.
                List<Type> beanTypes = new ArrayList<>();
                beanTypes.add(effectiveType);
                if (effectiveType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
                    beanTypes.add(ClassType.create(effectiveType.name()));
                }

                ClassInfo implClass = index.getClassByName(effectiveType.name());
                BeanConfigurator<Object> configurator;
                if (implClass == null) {
                    if (!typeName.startsWith("java.")) {
                        continue;
                    }
                    configurator = registrationContext.configure(effectiveType.name())
                            .identifier(mockKey)
                            .scope(scope)
                            .creator(MockBeanCreator.class)
                            .param("implementationClassName", typeName);
                } else {
                    configurator = registrationContext.configure(effectiveType.name())
                            .identifier(mockKey)
                            .scope(scope)
                            .creator(MockBeanCreator.class)
                            .param("implementationClass", implClass);
                }
                if (cleanedQualifiers.isEmpty()) {
                    configurator.addQualifier(
                            AnnotationInstance.builder(DotNames.DEFAULT).build());
                } else {
                    for (AnnotationInstance q : cleanedQualifiers) {
                        configurator.addQualifier(q);
                    }
                }
                for (Type t : beanTypes) {
                    configurator.addType(t);
                }
                configurator.done();
            }
        }

        private static boolean isJdkType(String typeName) {
            return typeName.startsWith("java.") || typeName.startsWith("jakarta.")
                    || typeName.startsWith("javax.");
        }

        /**
         * Whether the given type is bound to a synthetic bean producer
         * by DeltaSpike's {@code @PartialBeanBinding} — directly or via
         * a meta-annotation tree. Recognized by FQN so jawelte doesn't
         * have to compile-depend on DeltaSpike.
         */
        private boolean hasSyntheticBeanBinding(DotName typeName) {
            ClassInfo classInfo = index.getClassByName(typeName);
            if (classInfo == null) {
                return false;
            }
            return annotatedWithPartialBeanBinding(classInfo, new HashSet<>());
        }

        private boolean annotatedWithPartialBeanBinding(
                ClassInfo classInfo, Set<DotName> visited) {
            for (AnnotationInstance ann : classInfo.declaredAnnotations()) {
                DotName n = ann.name();
                if ("org.apache.deltaspike.partialbean.api.PartialBeanBinding"
                        .equals(n.toString())) {
                    return true;
                }
                if (!visited.add(n)) {
                    continue;
                }
                ClassInfo metaInfo = index.getClassByName(n);
                if (metaInfo != null && annotatedWithPartialBeanBinding(metaInfo, visited)) {
                    return true;
                }
            }
            return false;
        }

        private static String buildMockKey(
                Type type,
                Set<AnnotationInstance> qualifiers,
                Map<DotName, Set<String>> nonbindingMembers) {
            StringBuilder sb = new StringBuilder(type.toString());
            qualifiers.stream()
                    .sorted(java.util.Comparator.comparing(a -> a.name().toString()))
                    .forEach(q -> {
                        sb.append(':').append(q.name());
                        Set<String> nb = nonbindingMembers.getOrDefault(q.name(), Collections.emptySet());
                        for (AnnotationValue v : q.values()) {
                            if (!nb.contains(v.name())) {
                                sb.append('.').append(v.name()).append('=').append(v.toString());
                            }
                        }
                    });
            return sb.toString();
        }

        private static Set<AnnotationInstance> stripNonbindingValues(
                Set<AnnotationInstance> qualifiers, Map<DotName, Set<String>> nonbindingMembers) {
            Set<AnnotationInstance> result = new LinkedHashSet<>();
            for (AnnotationInstance q : qualifiers) {
                Set<String> nb = nonbindingMembers.get(q.name());
                if (nb != null && !nb.isEmpty()) {
                    var bld = AnnotationInstance.builder(q.name());
                    for (AnnotationValue v : q.values()) {
                        if (!nb.contains(v.name())) {
                            bld.add(v);
                        }
                    }
                    result.add(bld.build());
                } else {
                    result.add(q);
                }
            }
            return result;
        }

        private static boolean isBuiltInType(Type type, Set<AnnotationInstance> qualifiers) {
            String name = type.name().toString();
            if (name.startsWith("javax.") || name.startsWith("jakarta.")
                    || name.startsWith("io.quarkus.arc.")) {
                return true;
            }
            if (name.startsWith("java.")) {
                return !hasCustomQualifier(qualifiers);
            }
            return false;
        }

        private static boolean hasCustomQualifier(Set<AnnotationInstance> qualifiers) {
            for (AnnotationInstance q : qualifiers) {
                DotName n = q.name();
                if (!n.equals(DOT_DEFAULT) && !n.equals(DOT_ANY) && !n.equals(DOT_NAMED)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isSatisfied(
                Type requiredType,
                Set<AnnotationInstance> requiredQualifiers,
                List<BeanInfo> beans) {
            for (BeanInfo bean : beans) {
                if (bean.getTypes().stream().noneMatch(t -> t.equals(requiredType))) {
                    continue;
                }
                if (qualifiersMatch(requiredQualifiers, bean.getQualifiers())) {
                    return true;
                }
            }
            return false;
        }

        private static boolean qualifiersMatch(
                Set<AnnotationInstance> required, Collection<AnnotationInstance> beanQualifiers) {
            Set<DotName> beanNames = new HashSet<>();
            for (AnnotationInstance q : beanQualifiers) {
                beanNames.add(q.name());
            }
            for (AnnotationInstance rq : required) {
                DotName n = rq.name();
                if (n.equals(DOT_DEFAULT) || n.equals(DOT_ANY)) {
                    continue;
                }
                if (!beanNames.contains(n)) {
                    return false;
                }
            }
            return true;
        }
    }

    // ----- Resource output --------------------------------------------

    private static class GeneratedResourceOutput implements ResourceOutput {

        private final File testOutputDirectory;
        private final File componentsProviderFile;

        GeneratedResourceOutput(File testOutputDirectory, File componentsProviderFile) {
            this.testOutputDirectory = testOutputDirectory;
            this.componentsProviderFile = componentsProviderFile;
        }

        @Override
        public void writeResource(Resource resource) throws IOException {
            switch (resource.getType()) {
                case JAVA_CLASS:
                    resource.writeTo(testOutputDirectory);
                    break;
                case SERVICE_PROVIDER:
                    if (resource.getName().endsWith(ComponentsProvider.class.getName())) {
                        if (!componentsProviderFile.getParentFile().exists()) {
                            componentsProviderFile.getParentFile().mkdirs();
                        }
                        try (FileOutputStream out = new FileOutputStream(componentsProviderFile)) {
                            out.write(resource.getData());
                        }
                    }
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unsupported ResourceOutput type: " + resource.getType());
            }
        }
    }

    // ----- ArcTestClassLoader -----------------------------------------

    /**
     * ClassLoader that exposes the generated {@link ComponentsProvider}
     * service file to ArC so {@code Arc.initialize(...)} discovers the
     * generated bean definitions.
     */
    static class ArcTestClassLoader extends ClassLoader {

        private final File componentsProviderFile;

        ArcTestClassLoader(ClassLoader parent, File componentsProviderFile) {
            super(parent);
            this.componentsProviderFile = componentsProviderFile;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (("META-INF/services/" + ComponentsProvider.class.getName()).equals(name)
                    && componentsProviderFile.canRead()) {
                Enumeration<URL> parentResources = super.getResources(name);
                List<URL> list = new ArrayList<>();
                while (parentResources.hasMoreElements()) {
                    list.add(parentResources.nextElement());
                }
                list.add(componentsProviderFile.toURI().toURL());
                return Collections.enumeration(list);
            }
            return super.getResources(name);
        }
    }

    // ----- TestContext metadata holders -------------------------------

    /**
     * Wraps the original {@code Thread.currentThread()} context
     * classloader so {@code afterAll} can restore it after ArC
     * shutdown.
     *
     * @param classLoader the previous TCCL captured during bootstrap
     */
    public record CdiOldTccl(ClassLoader classLoader) {
    }

    /**
     * Wraps the active {@link RequestContextController} so
     * {@code afterEach} can deactivate it.
     *
     * @param controller the active controller bound by {@code beforeEach}
     */
    public record CdiRequestController(RequestContextController controller) {
    }

    /**
     * Captures an inline {@code @TestBean} field declaration.
     *
     * @param declaringClass the class on which the field is declared
     * @param fieldName      the field's declared name
     * @param fieldType      the field's declared type
     * @param qualifiers     CDI qualifiers carried on the field
     * @param scope          the user-declared CDI scope on the field,
     *                       or {@code null} when no scope annotation is
     *                       present (registrar consults
     *                       {@link BeanScopeMapper} or its built-in
     *                       default)
     */
    public record InlineField(
            Class<?> declaringClass,
            String fieldName,
            Class<?> fieldType,
            Set<Annotation> qualifiers,
            Class<? extends Annotation> scope) {
    }
}
