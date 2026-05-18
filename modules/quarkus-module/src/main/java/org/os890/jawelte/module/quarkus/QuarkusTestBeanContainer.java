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
package org.os890.jawelte.module.quarkus;

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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import jakarta.annotation.Priority;
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
import org.os890.jawelte.core.api.port.TestBeanContainerPort;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.quarkus.internal.InlineFieldBeanCreator;
import org.os890.jawelte.module.quarkus.internal.MockBeanCreator;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.ArcInitConfig;
import io.quarkus.arc.ComponentsProvider;
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
@Priority(100)
public class QuarkusTestBeanContainer implements TestBeanContainerPort {

    private static final String KEY_OLD_TCCL = "quarkus.oldTccl";
    private static final String KEY_REQUEST_CTRL = "quarkus.requestController";

    private static final String TARGET_TEST_CLASSES = "target/test-classes";
    private static final String TARGET_CLASSES = "target/classes";

    private static final DotName DOT_DEFAULT = DotName.createSimple("jakarta.enterprise.inject.Default");
    private static final DotName DOT_ANY = DotName.createSimple("jakarta.enterprise.inject.Any");
    private static final DotName DOT_NAMED = DotName.createSimple("jakarta.inject.Named");
    private static final DotName DEPENDENT = DotName.createSimple("jakarta.enterprise.context.Dependent");

    /** No-arg constructor required by {@code ServiceLoader}. */
    public QuarkusTestBeanContainer() {
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

        ClassLoader oldTccl = buildAndBootArc(
                testClass, beanClasses, selectedAlternatives, inlineFields, limitToTestBeans);
        testContext.bindMetadata(ClassLoader.class, oldTccl);
        testContext.getMetadata(ClassLoader.class)
                .ifPresent(cl -> testContext.bindMetadata(QuarkusOldTccl.class, new QuarkusOldTccl(cl)));
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
        testContext.bindMetadata(QuarkusRequestController.class, new QuarkusRequestController(controller));
    }

    @Override
    public void afterEach(TestContext testContext) {
        testContext.getMetadata(QuarkusRequestController.class).ifPresent(holder -> {
            holder.controller().deactivate();
            testContext.unbindMetadata(QuarkusRequestController.class);
        });
    }

    @Override
    public void afterAll(TestContext testContext) {
        Arc.shutdown();
        testContext.getMetadata(QuarkusOldTccl.class).ifPresent(holder -> {
            Thread.currentThread().setContextClassLoader(holder.classLoader());
            testContext.unbindMetadata(QuarkusOldTccl.class);
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
                        ? new Annotation[] { jakarta.enterprise.inject.Default.Literal.INSTANCE }
                        : qualifiers.toArray(new Annotation[0]);
                Object value = container.instance(field.getType(), qArr).get();
                field.set(testInstance, value);
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new IllegalStateException(
                        "Failed to inject field " + clazz.getName() + "." + field.getName(), e);
            }
        }
    }

    // ----- ArC build + boot ----------------------------------------------

    private ClassLoader buildAndBootArc(
            Class<?> testClass,
            Set<Class<?>> beanClasses,
            Set<Class<?>> selectedAlternatives,
            Set<InlineField> inlineFields,
            boolean limitToTestBeans) {
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
            // see them. @Singleton is the cheapest scope that keeps
            // the test class as one instance per ArC session.
            DotName testClassDotName = DotName.createSimple(testClass.getName());
            builder.addAnnotationTransformation(
                    AnnotationTransformation.forClasses()
                            .whenClass(c -> c.name().equals(testClassDotName))
                            .transform(ctx -> {
                                if (!hasScope(ctx)) {
                                    ctx.add(AnnotationInstance.builder(DotNames.SINGLETON).build());
                                }
                            }));

            if (!altDotNames.isEmpty()) {
                builder.addAnnotationTransformation(
                        AnnotationTransformation.forClasses()
                                .whenClass(c -> altDotNames.contains(c.name()))
                                .transform(ctx -> {
                                    if (!ctx.hasAnnotation(DotNames.PRIORITY)) {
                                        ctx.add(AnnotationInstance.builder(DotNames.PRIORITY)
                                                .add("value", Integer.MAX_VALUE).build());
                                    }
                                    if (!hasScope(ctx)) {
                                        ctx.add(AnnotationInstance.builder(DotNames.SINGLETON).build());
                                    }
                                }));
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

            builder.addBeanRegistrar(new MockAndInlineBeanRegistrar(inlineFields, computingIndex));
            builder.setRemoveUnusedBeans(false);
            builder.setOutput(new GeneratedResourceOutput(testOutputDirectory, componentsProviderFile));

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
        for (Class<?> clazz : classes) {
            indexClass(indexer, clazz);
        }
        return indexer.complete();
    }

    private static void indexClass(Indexer indexer, Class<?> clazz) throws IOException {
        if (clazz == null || clazz == Object.class) {
            return;
        }
        String resourceName = clazz.getName().replace('.', '/') + ".class";
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = QuarkusTestBeanContainer.class.getClassLoader();
        }
        try (InputStream stream = cl.getResourceAsStream(resourceName)) {
            if (stream != null) {
                indexer.index(stream);
            }
        }
        if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
            indexClass(indexer, clazz.getSuperclass());
        }
        for (Class<?> iface : clazz.getInterfaces()) {
            indexClass(indexer, iface);
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
                addClassIfNotVoid(tb.bean(), selected);
                addClassIfNotVoid(tb.beanProducer(), selected);
            } else if (ann instanceof TestBeans tbs) {
                for (TestBean tb : tbs.value()) {
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
            Set<Annotation> qualifiers = new LinkedHashSet<>();
            for (Annotation ann : field.getAnnotations()) {
                if (ann.annotationType().isAnnotationPresent(jakarta.inject.Qualifier.class)) {
                    qualifiers.add(ann);
                }
            }
            qualifiers.removeIf(a -> a.annotationType() == TestBean.class);
            fields.add(new InlineField(testClass, field.getName(), field.getType(), qualifiers));
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
                    if (!name.endsWith(".class") || name.contains("$")) {
                        continue;
                    }
                    if (isVendorOrJdkPath(name)) {
                        continue;
                    }
                    String className = name.replace('/', '.').replace(".class", "");
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
                || classFilePath.startsWith("org/os890/jawelte/module/quarkus/")
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
                if (className.contains("$") || className.startsWith("META-INF")) {
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

    // ----- Scope / type utilities --------------------------------------

    private static boolean hasScope(AnnotationTransformation.TransformationContext ctx) {
        return ctx.hasAnnotation(DotNames.SINGLETON)
                || ctx.hasAnnotation(DEPENDENT)
                || ctx.hasAnnotation(DotNames.APPLICATION_SCOPED)
                || ctx.hasAnnotation(DotName.createSimple(RequestScoped.class.getName()));
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

        MockAndInlineBeanRegistrar(Set<InlineField> inlineFields, IndexView index) {
            this.inlineFields = inlineFields;
            this.index = index;
        }

        @Override
        public void register(RegistrationContext registrationContext) {
            Set<DotName> inlineFieldTypes = registerInlineFieldBeans(registrationContext);
            Map<DotName, Set<String>> nonbindingMembers =
                    readNonbindingMembers(registrationContext);
            registerMocksForUnsatisfiedInjectionPoints(
                    registrationContext, inlineFieldTypes, nonbindingMembers);
        }

        private Set<DotName> registerInlineFieldBeans(RegistrationContext registrationContext) {
            Set<DotName> inlineFieldTypes = new HashSet<>();
            for (InlineField field : inlineFields) {
                DotName typeName = DotName.createSimple(field.fieldType().getName());
                BeanConfigurator<Object> configurator = registrationContext
                        .configure(typeName)
                        .scope(Singleton.class)
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
                if (builtin != null && builtin != BuiltinBean.INSTANCE
                        && builtin != BuiltinBean.LIST) {
                    continue;
                }
                Type requiredType = ip.getRequiredType();
                Set<AnnotationInstance> requiredQualifiers = ip.getRequiredQualifiers();
                if (isBuiltInType(requiredType, requiredQualifiers)) {
                    continue;
                }
                if (inlineFieldTypes.contains(requiredType.name())) {
                    continue;
                }
                if (isSatisfied(requiredType, requiredQualifiers, beans)) {
                    continue;
                }
                String mockKey = buildMockKey(requiredType, requiredQualifiers, nonbindingMembers);
                if (!registeredMockKeys.add(mockKey)) {
                    continue;
                }
                Set<AnnotationInstance> cleanedQualifiers =
                        stripNonbindingValues(requiredQualifiers, nonbindingMembers);
                ClassInfo implClass = index.getClassByName(requiredType.name());
                if (implClass == null) {
                    String typeName = requiredType.name().toString();
                    if (!typeName.startsWith("java.")) {
                        continue;
                    }
                    registrationContext.configure(requiredType.name())
                            .identifier(mockKey)
                            .scope(Singleton.class)
                            .addType(requiredType)
                            .qualifiers(cleanedQualifiers.toArray(new AnnotationInstance[0]))
                            .creator(MockBeanCreator.class)
                            .param("implementationClassName", typeName)
                            .defaultBean()
                            .done();
                    continue;
                }
                registrationContext.configure(requiredType.name())
                        .identifier(mockKey)
                        .scope(Singleton.class)
                        .addType(requiredType)
                        .qualifiers(cleanedQualifiers.toArray(new AnnotationInstance[0]))
                        .creator(MockBeanCreator.class)
                        .param("implementationClass", implClass)
                        .defaultBean()
                        .done();
            }
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
     */
    public record QuarkusOldTccl(ClassLoader classLoader) {
    }

    /**
     * Wraps the active {@link RequestContextController} so
     * {@code afterEach} can deactivate it.
     */
    public record QuarkusRequestController(RequestContextController controller) {
    }

    /**
     * Captures an inline {@code @TestBean} field declaration: the
     * declaring class, field name, declared field type, and the set
     * of CDI qualifiers on the field.
     */
    public record InlineField(
            Class<?> declaringClass,
            String fieldName,
            Class<?> fieldType,
            Set<Annotation> qualifiers) {
    }
}
