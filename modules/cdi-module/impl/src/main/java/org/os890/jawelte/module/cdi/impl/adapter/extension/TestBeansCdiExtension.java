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
package org.os890.jawelte.module.cdi.impl.adapter.extension;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AfterTypeDiscovery;
import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Qualifier;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.ConfigProvider;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.port.BeanScopeMapperPort;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.api.port.ExcludedPackageFilter;
import org.os890.jawelte.module.cdi.api.port.MockFactory;
import org.os890.jawelte.module.cdi.api.port.WhitelistFilter;
import org.os890.jawelte.module.cdi.impl.util.SyntheticBeanUtil;
import org.os890.jawelte.module.cdi.impl.util.TestBeanScanner;

/**
 * CDI Extension shipped by cdi-module. Discovers {@code @TestBean}
 * declarations on the active test class, registers
 * {@code @Alternative} and synthetic beans, applies the
 * {@code limitToTestBeans=true} whitelist veto when in scope, and
 * synthesises Mockito mocks for unsatisfied injection points.
 *
 * <p>The IP-collection step at {@code ProcessInjectionPoint} time
 * delegates to {@link ExcludedPackageFilter#isOwningBeanExcluded(Class)}
 * to drop IPs declared by framework-internal beans (Weld / OWB /
 * DeltaSpike / SmallRye decorators, interceptors, producers) before
 * they enter the auto-mock candidate set; the default
 * {@code ExcludedPackageFilter} ships the prefix list via MP Config
 * (see {@code META-INF/microprofile-config.properties}), so the
 * filter is fully configurable through the standard MP Config
 * machinery.
 *
 * <p>The Extension obtains the active {@link TestContext} via
 * {@link TestContext#get()} during {@code BeforeBeanDiscovery};
 * outside of the {@code DelegatingJUnitExtension.beforeAll}
 * bootstrap window {@code TestContext.get()} throws and the
 * Extension propagates that as a configuration error.
 *
 * <p>Loaded by the CDI runtime via the
 * {@code META-INF/services/jakarta.enterprise.inject.spi.Extension}
 * registration shipped in this module.
 */
public class TestBeansCdiExtension implements Extension {

    /**
     * MP Config key whose value is the FQCN of the CDI scope
     * annotation to assign to auto-mock synthetic beans of
     * non-JDK target types. scope-module/impl ships a
     * {@code microprofile-config.properties} default of
     * {@code org.os890.jawelte.module.scope.api.TestMethodScoped};
     * consumers override by setting the same key in any
     * higher-priority MP Config source.
     */
    public static final String AUTO_MOCK_DEFAULT_SCOPE_KEY =
            "org.os890.jawelte.module.cdi.auto-mock.default-scope";

    private static final Class<? extends Annotation> AUTO_MOCK_NON_JDK_SCOPE = resolveAutoMockNonJdkScope();

    private TestContext activeContext;
    private TestBeanScanner.Result scanResult;
    private boolean limitToTestBeans;
    private WhitelistFilter whitelistFilter;
    private ExcludedPackageFilter excludedPackageFilter;
    private MockFactory mockFactory;
    private final Set<IpKey> unsatisfiedCandidateIps = new LinkedHashSet<>();

    /** No-arg constructor required by the CDI runtime. */
    public TestBeansCdiExtension() {
    }

    void onBeforeBeanDiscovery(@Observes BeforeBeanDiscovery event) {
        TestContext active;
        try {
            active = TestContext.get();
        } catch (IllegalStateException noActiveContext) {
            // No active TestContext: the container is being booted outside
            // jawelte's bootstrap window (e.g. @EnableTestBeans(manageContainer=false)
            // with the user booting the container in @BeforeAll). The Extension
            // becomes a no-op; the user owns @TestBean activation and auto-mocking.
            return;
        }
        this.activeContext = active;
        Class<?> testClass = active.getTestClass();

        Optional<EnableTestBeans> config = findEnableTestBeans(testClass);
        this.limitToTestBeans = config.map(EnableTestBeans::limitToTestBeans).orElse(false);

        this.scanResult = TestBeanScanner.scan(testClass);
        active.bindMetadata(TestBeanScanner.Result.class, scanResult);

        this.whitelistFilter = TestContext.loadService(WhitelistFilter.class);
        this.excludedPackageFilter = TestContext.loadService(ExcludedPackageFilter.class);
        this.mockFactory = TestContext.loadService(MockFactory.class);

        // Warm both filter caches on the bootstrap thread. Weld dispatches
        // ProcessInjectionPoint events on ForkJoinPool worker threads whose
        // context ClassLoader does not include this module's classpath, so a
        // lazy MP Config read on first PIP would fail with ClassNotFoundException
        // from TestContext.instantiateConfigured's Class.forName. Touching the
        // filter here fills its cached prefix lists while we still hold the
        // bootstrap thread's ClassLoader.
        if (excludedPackageFilter != null) {
            excludedPackageFilter.isOwningBeanExcluded(Object.class);
            excludedPackageFilter.isExcluded(Object.class);
        }

        // Force discovery of @TestBean target classes that lack a
        // bean-defining annotation (e.g. @Alternative without an explicit
        // scope). Augment the AnnotatedType with @Dependent so CDI accepts
        // the resulting bean.
        for (Class<?> beanType : scanResult.beanTypes()) {
            forceDiscoveryWithDependentFallback(event, beanType);
        }
        for (Class<?> producerType : scanResult.producerTypes()) {
            forceDiscoveryWithDependentFallback(event, producerType);
        }
    }

    void onProcessAnnotatedType(@Observes ProcessAnnotatedType<?> event) {
        if (!limitToTestBeans) {
            return;
        }
        Class<?> rawType = event.getAnnotatedType().getJavaClass();
        if (whitelistFilter != null && !whitelistFilter.isAllowed(rawType)) {
            event.veto();
        }
    }

    <T, X> void onProcessInjectionPoint(@Observes ProcessInjectionPoint<T, X> event) {
        InjectionPoint ip = event.getInjectionPoint();
        Type ipType = ip.getType();
        Set<Annotation> qualifiers = ip.getQualifiers();
        Type targetType = unwrapWrapper(ipType);
        if (targetType == null) {
            return;
        }
        if (excludedPackageFilter != null) {
            Class<?> owningBeanClass = ip.getBean() == null ? null : ip.getBean().getBeanClass();
            if (owningBeanClass != null && excludedPackageFilter.isOwningBeanExcluded(owningBeanClass)) {
                return;
            }
        }
        unsatisfiedCandidateIps.add(new IpKey(targetType, new LinkedHashSet<>(qualifiers)));
    }

    void onAfterTypeDiscovery(@Observes AfterTypeDiscovery event) {
        if (scanResult == null) {
            return;
        }
        List<Class<?>> alternatives = event.getAlternatives();
        for (Class<?> beanType : scanResult.beanTypes()) {
            alternatives.add(beanType);
        }
        for (Class<?> producerType : scanResult.producerTypes()) {
            alternatives.add(producerType);
        }
    }

    void onAfterBeanDiscovery(
            @Observes AfterBeanDiscovery event,
            BeanManager beanManager) {
        if (scanResult == null) {
            return;
        }

        // Static-field synthetic beans. Scope precedence:
        //   1. CDI scope annotation declared by the user on the field
        //      (any annotation meta-annotated with @NormalScope or
        //      @Scope - covers @TestClassScoped, @RequestScoped,
        //      @Singleton, @Dependent, custom user scopes, ...).
        //   2. BeanScopeMapper provider triggered by @TestBean
        //      (scope-module ships one with target @TestClassScoped;
        //      queried via BeanScopeMapperPort.mapScope(Field)).
        //   3. cdi-module's @Singleton fallback.
        BeanScopeMapperPort scopeMapperPort = TestContext.loadService(BeanScopeMapperPort.class);
        for (TestBeanScanner.StaticField staticField : scanResult.staticFields()) {
            Field field = staticField.field();
            Set<Annotation> qualifiers = collectFieldQualifiers(field);
            Class<? extends Annotation> scope = userDeclaredScopeOnField(field)
                    .or(() -> scopeMapperPort.mapScope(field))
                    .orElse(Singleton.class);
            SyntheticBeanUtil.registerStaticFieldBean(
                    event, field.getType(), staticField.value(), qualifiers, scope);
        }

        if (limitToTestBeans) {
            // Auto-mocking is disabled in whitelist mode. CDI's own
            // deployment validation surfaces unsatisfied IPs.
            return;
        }

        // Collect IPs from the test class itself (not a CDI bean,
        // therefore no ProcessInjectionPoint events fire for it).
        addTestClassInjectionPoints(beanManager);

        // Synthesise mocks for unsatisfied collected IPs. Each
        // (targetType, qualifier-set) pair gets its own bean so two
        // distinct qualifier types (or two distinct binding qualifier
        // member values) on the same target type produce two
        // independent mocks. The IpKey set already deduplicated
        // @Nonbinding-equivalent IPs at collection time.
        //
        // Auto-mock scope precedence (only applies to non-JDK types;
        // JDK types are always @Dependent because the normal-scope
        // proxy cannot subclass final JDK classes):
        //   1. MP Config key `org.os890.jawelte.module.cdi.auto-mock.default-scope`
        //      (scope-module's microprofile-config.properties supplies
        //      the default `org.os890.jawelte.module.scope.api.TestMethodScoped`
        //      when scope-module is on the classpath; any consumer
        //      overrides by setting the same key in a higher-priority
        //      MP Config source). The value is the FQCN of a CDI scope
        //      annotation class; resolved reflectively.
        //   2. cdi-module's @RequestScoped fallback (when the key is
        //      unset or the configured class isn't loadable).
        // The scope is resolved once at class-load time into
        // AUTO_MOCK_NON_JDK_SCOPE; ConfigProvider.getConfig() runs
        // exactly once per JVM for this extension regardless of how
        // many test classes bootstrap a CDI container.
        for (IpKey key : unsatisfiedCandidateIps) {
            Type targetType = key.targetType;
            Class<?> rawType = rawClassOf(targetType);
            if (rawType == null) {
                continue;
            }
            Set<Annotation> qualifiers = key.qualifiers;
            if (hasSyntheticBeanBinding(rawType)) {
                continue;
            }
            if (excludedPackageFilter != null && excludedPackageFilter.isExcluded(rawType)) {
                continue;
            }
            if (scanResult.isTarget(rawType)) {
                // Explicit @TestBean declaration wins; no auto-mock for
                // the user's declared target type.
                continue;
            }
            if (!isUnsatisfied(beanManager, targetType, qualifiers)) {
                continue;
            }
            Object mockSample = mockFactory.create(rawType);
            if (mockSample == null) {
                continue;
            }
            // Supplier indirection so each bean lookup gets a fresh mock
            // per scope activation.
            SyntheticBeanUtil.registerAutoMockBean(
                    event, rawType, targetType, qualifiers,
                    () -> mockFactory.create(rawType),
                    AUTO_MOCK_NON_JDK_SCOPE);
        }
    }

    /**
     * Test-only accessor exposed for tests that need to inspect what
     * the Extension collected during bootstrap. Production users
     * should not depend on this surface.
     *
     * @return a defensive copy of the collected unsatisfied-IP candidates
     */
    Set<IpKey> unsatisfiedCandidateIpsForTests() {
        return new LinkedHashSet<>(unsatisfiedCandidateIps);
    }

    private static void forceDiscoveryWithDependentFallback(BeforeBeanDiscovery event, Class<?> target) {
        if (hasBeanDefiningAnnotation(target)) {
            return;
        }
        if (!target.isAnnotationPresent(Alternative.class)) {
            // Spec: @TestBean(bean=X) where X has no @Alternative is a
            // silent no-op (per scenario 35). Don't promote it to a
            // @Dependent bean.
            return;
        }
        event.addAnnotatedType(target, target.getName())
                .add(Dependent.Literal.INSTANCE);
    }

    private static boolean hasBeanDefiningAnnotation(Class<?> target) {
        for (Annotation a : target.getAnnotations()) {
            Class<? extends Annotation> at = a.annotationType();
            if (at.isAnnotationPresent(NormalScope.class)) {
                return true;
            }
            if (at.isAnnotationPresent(Scope.class)) {
                return true;
            }
            if (at.isAnnotationPresent(Stereotype.class)) {
                return true;
            }
            if (at.equals(Dependent.class) || at.equals(Singleton.class)) {
                return true;
            }
        }
        return false;
    }

    private static boolean qualifiersEquivalent(Annotation a, Annotation b) {
        if (!a.annotationType().equals(b.annotationType())) {
            return false;
        }
        for (Method member : a.annotationType().getDeclaredMethods()) {
            if (member.isAnnotationPresent(Nonbinding.class)) {
                continue;
            }
            try {
                if (!Objects.deepEquals(member.invoke(a), member.invoke(b))) {
                    return false;
                }
            } catch (ReflectiveOperationException e) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> rawClassOf(Type type) {
        if (type instanceof Class<?> cls) {
            return cls;
        }
        if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> cls) {
            return cls;
        }
        return null;
    }

    /**
     * Whether the type carries an annotation that is itself
     * meta-annotated with a third-party "synthetic-bean binding"
     * marker. Currently recognized: Apache DeltaSpike's
     * {@code org.apache.deltaspike.partialbean.api.PartialBeanBinding}
     * (compared by FQN string so cdi-module incurs no compile-time
     * dependency on DeltaSpike). When this returns {@code true} the
     * Extension skips auto-mock registration; the third-party
     * extension is expected to register the bean itself.
     *
     * @param rawType the unsatisfied IP raw type
     * @return {@code true} if a third-party extension owns this type
     */
    private static boolean hasSyntheticBeanBinding(Class<?> rawType) {
        for (Annotation directAnnotation : rawType.getAnnotations()) {
            for (Annotation metaAnnotation : directAnnotation.annotationType().getAnnotations()) {
                if ("org.apache.deltaspike.partialbean.api.PartialBeanBinding"
                        .equals(metaAnnotation.annotationType().getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void addTestClassInjectionPoints(BeanManager beanManager) {
        Class<?> testClass = activeContext.getTestClass();
        AnnotatedType<?> testClassType = beanManager.createAnnotatedType(testClass);
        for (AnnotatedField<?> field : testClassType.getFields()) {
            if (!field.isAnnotationPresent(Inject.class)) {
                continue;
            }
            Type targetType = unwrapWrapper(field.getBaseType());
            if (targetType == null) {
                continue;
            }
            Set<Annotation> qualifiers = new LinkedHashSet<>();
            for (Annotation annotation : field.getAnnotations()) {
                if (beanManager.isQualifier(annotation.annotationType())) {
                    qualifiers.add(annotation);
                }
            }
            unsatisfiedCandidateIps.add(new IpKey(targetType, qualifiers));
        }
    }

    private static boolean isUnsatisfied(
            BeanManager beanManager, Type targetType, Set<Annotation> qualifiers) {
        Annotation[] qualifierArray = qualifiers.toArray(new Annotation[0]);
        return beanManager.getBeans(targetType, qualifierArray).isEmpty();
    }

    private static Type unwrapWrapper(Type ipType) {
        if (ipType instanceof Class<?>) {
            return ipType;
        }
        if (ipType instanceof ParameterizedType pt) {
            Type rawType = pt.getRawType();
            if (rawType == Provider.class || rawType == Instance.class) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length == 1) {
                    return args[0];
                }
                return null;
            }
            return ipType;
        }
        return null;
    }

    private static Set<Annotation> collectFieldQualifiers(Field field) {
        Set<Annotation> qualifiers = new LinkedHashSet<>();
        for (Annotation annotation : field.getAnnotations()) {
            if (annotation.annotationType().isAnnotationPresent(Qualifier.class)) {
                qualifiers.add(annotation);
            }
        }
        return Collections.unmodifiableSet(qualifiers);
    }

    /**
     * Whether the field carries a CDI scope annotation declared by
     * the test author. Recognises any annotation type meta-annotated
     * with {@link NormalScope} or {@link Scope} — covers
     * {@code @RequestScoped}, {@code @ApplicationScoped},
     * {@code @Singleton}, {@code @Dependent}, scope-module's
     * {@code @TestMethodScoped} / {@code @TestClassScoped}, and any
     * custom user scope.
     *
     * @param field the static field declaring a {@code @TestBean}
     * @return the user-declared CDI scope annotation type, or empty
     *         when the field has no scope annotation
     */
    private static Optional<Class<? extends Annotation>> userDeclaredScopeOnField(Field field) {
        for (Annotation annotation : field.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (annotationType.isAnnotationPresent(NormalScope.class)
                    || annotationType.isAnnotationPresent(Scope.class)) {
                return Optional.of(annotationType);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolve the default CDI scope for auto-mock synthetic beans
     * of non-JDK target types. Invoked exactly once per JVM (per
     * ClassLoader) from the {@link #AUTO_MOCK_NON_JDK_SCOPE} static
     * initializer; subsequent reads on every CDI bootstrap consult
     * the cached static field, so {@link ConfigProvider#getConfig()}
     * runs only once for this extension.
     *
     * <p>Reads MP Config key
     * {@value #AUTO_MOCK_DEFAULT_SCOPE_KEY}; the value is the FQCN
     * of a CDI scope annotation. scope-module/impl supplies
     * {@code org.os890.jawelte.module.scope.api.TestMethodScoped}
     * via its {@code META-INF/microprofile-config.properties}
     * default; consumers override the value in any higher-priority
     * MP Config source. The class is loaded reflectively — when
     * the configured class isn't on the runtime classpath (e.g.
     * scope-module not deployed), the method falls back to
     * {@link RequestScoped @RequestScoped}.
     *
     * @return the auto-mock default scope for non-JDK targets;
     *         never {@code null}
     */
    private static Class<? extends Annotation> resolveAutoMockNonJdkScope() {
        Optional<String> configured = ConfigProvider.getConfig()
                .getOptionalValue(AUTO_MOCK_DEFAULT_SCOPE_KEY, String.class)
                .map(String::trim)
                .filter(value -> !value.isEmpty());
        if (configured.isEmpty()) {
            return RequestScoped.class;
        }
        try {
            Class<?> loaded = Class.forName(
                    configured.get(),
                    true,
                    Thread.currentThread().getContextClassLoader());
            if (!Annotation.class.isAssignableFrom(loaded)) {
                return RequestScoped.class;
            }
            @SuppressWarnings("unchecked")
            Class<? extends Annotation> scope = (Class<? extends Annotation>) loaded;
            return scope;
        } catch (ClassNotFoundException | LinkageError missing) {
            return RequestScoped.class;
        }
    }

    private static Optional<EnableTestBeans> findEnableTestBeans(Class<?> testClass) {
        EnableTestBeans direct = testClass.getAnnotation(EnableTestBeans.class);
        if (direct != null) {
            return Optional.of(direct);
        }
        Set<Class<? extends Annotation>> visited = new HashSet<>();
        for (Annotation annotation : testClass.getAnnotations()) {
            EnableTestBeans found = walkMetaAnnotations(annotation, visited);
            if (found != null) {
                return Optional.of(found);
            }
        }
        return Optional.empty();
    }

    private static EnableTestBeans walkMetaAnnotations(
            Annotation annotation, Set<Class<? extends Annotation>> visited) {
        Class<? extends Annotation> annotationType = annotation.annotationType();
        if (!visited.add(annotationType)) {
            return null;
        }
        String packageName = annotationType.getPackageName();
        if (packageName.startsWith("java.") || packageName.startsWith("jakarta.")) {
            return null;
        }
        for (Annotation meta : annotationType.getAnnotations()) {
            if (meta instanceof EnableTestBeans found) {
                return found;
            }
            EnableTestBeans nested = walkMetaAnnotations(meta, visited);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    /**
     * Composite key for an unsatisfied injection point: target type plus
     * its qualifier set. Equality and hashCode use CDI qualifier
     * equivalence so two qualifier annotations of the same type whose
     * non-{@code @Nonbinding} member values match collapse to a single
     * key (matching CDI resolution), while two qualifiers of different
     * types — or two qualifiers of the same type with different binding
     * member values — stay distinct (so they get independent synthetic
     * mocks).
     */
    static class IpKey {

        private final Type targetType;
        private final Set<Annotation> qualifiers;

        IpKey(Type targetType, Set<Annotation> qualifiers) {
            this.targetType = targetType;
            this.qualifiers = qualifiers;
        }

        Type targetType() {
            return targetType;
        }

        Set<Annotation> qualifiers() {
            return qualifiers;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof IpKey other)) {
                return false;
            }
            if (!targetType.equals(other.targetType)) {
                return false;
            }
            if (qualifiers.size() != other.qualifiers.size()) {
                return false;
            }
            for (Annotation mine : qualifiers) {
                boolean matched = false;
                for (Annotation theirs : other.qualifiers) {
                    if (qualifiersEquivalent(mine, theirs)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            int h = targetType.hashCode();
            for (Annotation q : qualifiers) {
                h += qualifierHashCode(q);
            }
            return h;
        }

        private static int qualifierHashCode(Annotation annotation) {
            int h = annotation.annotationType().hashCode();
            for (Method member : annotation.annotationType().getDeclaredMethods()) {
                if (member.isAnnotationPresent(Nonbinding.class)) {
                    continue;
                }
                try {
                    Object value = member.invoke(annotation);
                    if (value != null) {
                        h = 31 * h + value.hashCode();
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Fall through with the running accumulator; missing
                    // member contribution is acceptable - equals() does
                    // the authoritative comparison and treats reflection
                    // failures as inequality.
                }
            }
            return h;
        }
    }
}
