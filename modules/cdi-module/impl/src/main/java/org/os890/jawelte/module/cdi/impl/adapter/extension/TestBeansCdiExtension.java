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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AfterTypeDiscovery;
import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.BeforeShutdown;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.InjectionTarget;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Qualifier;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;
import jakarta.interceptor.Interceptor;

import org.eclipse.microprofile.config.ConfigProvider;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.SuppliedTypeRegistry;
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
 * <p>Before synthesising anything the auto-mock loop consults
 * {@link SuppliedTypeRegistry} on the active {@code TestContext} and
 * skips the types other modules have already supplied. The
 * {@code isUnsatisfied} re-check alone cannot cover them:
 * {@code addBean()} registrations are invisible to
 * {@code BeanManager.getBeans(...)} for the whole of
 * {@code AfterBeanDiscovery}, so without the registry auto-mock
 * registers a competing bean of the same type and the deployment fails
 * with {@code AmbiguousResolutionException}. This observer therefore
 * runs late, after the suppliers have recorded.
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

    private TestContext activeContext;
    private TestBeanScanner.Result scanResult;
    private boolean limitToTestBeans;
    private WhitelistFilter whitelistFilter;
    private ExcludedPackageFilter excludedPackageFilter;
    private MockFactory mockFactory;
    // Resolved per container in onBeforeBeanDiscovery (not once per JVM)
    // so each test class's MP Config layer selects the auto-mock scope.
    private Class<? extends Annotation> autoMockNonJdkScope;
    // Concurrent: Weld dispatches ProcessInjectionPoint events on multiple
    // (ForkJoinPool) threads, so this set is mutated from several threads.
    private final Set<IpKey> unsatisfiedCandidateIps = ConcurrentHashMap.newKeySet();
    private final List<ProducedTestInstance> producedTestInstances = new ArrayList<>();

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
        // Resolve the auto-mock default scope here (per container, on the
        // bootstrap thread) so the active MP Config layer wins and the
        // reflective Class.forName runs under the bootstrap ClassLoader.
        this.autoMockNonJdkScope = resolveAutoMockNonJdkScope();

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
        // The test class itself is registered as a synthetic
        // @Dependent CDI bean at AfterBeanDiscovery time (see
        // onAfterBeanDiscovery). Doing it that late means it lands
        // after every auto-mock and every @TestBean alternative,
        // matching the unmanaged shape the framework used before
        // TICKET-016 — its @Inject fields don't fire
        // ProcessInjectionPoint and aren't "seen" by the rest of the
        // bean archive until the producer runs at
        // CDI.current().select(testClass).get() time.
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

    /**
     * Runs late on purpose. Auto-mocking must see what every other
     * module has supplied to this container, and a module records that
     * as it registers, so this observer is ordered after theirs with
     * {@code LIBRARY_AFTER}. See {@link SuppliedTypeRegistry}.
     */
    void onAfterBeanDiscovery(
            @Observes @Priority(Interceptor.Priority.LIBRARY_AFTER) AfterBeanDiscovery event,
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
            registerTestClassSyntheticBean(event, beanManager);
            return;
        }

        // The test class is kept out of CDI's normal bean discovery
        // (see onBeforeBeanDiscovery). Walk its @Inject fields here so
        // every unsatisfied IP joins the auto-mock candidate set in
        // time for the synthetic-bean loop below.
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
        // The scope was resolved per container in onBeforeBeanDiscovery
        // (into the autoMockNonJdkScope field), so each test class's
        // MP Config layer selects it rather than the value frozen by the
        // first container in the JVM.
        // What other modules have already supplied to this container.
        // They record it as they register, because the isUnsatisfied
        // re-check below runs on getBeans(...), which does not see
        // addBean() registrations while AfterBeanDiscovery is still in
        // progress - on either runtime, at any observer priority. This
        // observer runs late (see @Priority above) so the suppliers have
        // recorded before it looks. See SuppliedTypeRegistry.
        SuppliedTypeRegistry suppliedTypes = SuppliedTypeRegistry.of(activeContext);

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
            if (suppliedTypes.isSupplied(targetType) || suppliedTypes.isSupplied(rawType)) {
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
                    autoMockNonJdkScope);
        }

        // Finally, register the test class as a synthetic @Dependent
        // CDI bean. The producer instantiates via CDI's InjectionTarget
        // mechanism so jawelte's auto-mocks (registered just above) and
        // any @TestBean alternatives are visible during the test
        // class's field injection. The factory bridge in core/impl
        // resolves this synthetic bean via
        // CDI.current().select(testClass).get() and hands the result
        // to JUnit.
        registerTestClassSyntheticBean(event, beanManager);
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

    /**
     * Register the test class as a {@code @Dependent} synthetic CDI
     * bean inside {@code AfterBeanDiscovery}. The bean's producer
     * creates the test instance via CDI's
     * {@link jakarta.enterprise.inject.spi.InjectionTarget} machinery
     * — populating the test class's {@code @Inject} fields against
     * the current bean archive — and the {@code @Dependent} scope
     * guarantees no normal-scope proxy wraps the result, so private
     * fields and package-private test classes keep working.
     *
     * <p>Registering this late (after every auto-mock and every
     * explicit {@code @TestBean} alternative is in place) keeps the
     * test class invisible to CDI's regular bean-discovery pipeline:
     * its {@code @Inject} fields never fire
     * {@code ProcessInjectionPoint} events, mirroring the unmanaged
     * shape jawelte used previously. The auto-mock collector
     * sees those IPs via the explicit
     * {@link #addTestClassInjectionPoints(BeanManager)} walk above
     * instead.
     *
     * @param event       the {@code AfterBeanDiscovery} event
     * @param beanManager the active {@link BeanManager}; captured
     *                    inside the producer to lazily build the
     *                    {@link jakarta.enterprise.inject.spi.InjectionTarget}
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerTestClassSyntheticBean(
            AfterBeanDiscovery event, BeanManager beanManager) {
        Class<?> testClass = activeContext.getTestClass();
        if (hasBeanDefiningAnnotation(testClass)) {
            // User declared an explicit scope on the test class —
            // honour it and skip the synthetic registration. CDI's
            // regular discovery picks up the user-declared bean.
            return;
        }
        // Pre-build an InjectionTarget once to collect the declared
        // IPs that addInjectionPoints needs at registration time.
        // The producer below builds a fresh InjectionTarget at runtime
        // because OWB's InjectionTarget instance is only valid in the
        // bean-discovery window it was created in; calling
        // inject(...) on a discovery-time IT during the runtime
        // producer phase silently no-ops on the field set.
        AnnotatedType<?> annotatedType = beanManager.createAnnotatedType(testClass);
        InjectionTarget discoveryInjectionTarget = beanManager
                .getInjectionTargetFactory(annotatedType)
                .createInjectionTarget(null);
        // Declaring the test class's IPs on the synthetic bean lets
        // CDI's deployment validation surface unsatisfied dependencies
        // the same way it would for any managed bean — matching the
        // pre-TICKET-016 behaviour where unsatisfied IPs failed the
        // container bootstrap.
        event.addBean()
                .beanClass(testClass)
                .scope(Dependent.class)
                .types(testClass, Object.class)
                .qualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE)
                .addInjectionPoints(discoveryInjectionTarget.getInjectionPoints())
                .produceWith(instance -> instantiate(beanManager, testClass));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object instantiate(BeanManager beanManager, Class<?> testClass) {
        AnnotatedType<?> annotatedType = beanManager.createAnnotatedType(testClass);
        InjectionTarget injectionTarget = beanManager
                .getInjectionTargetFactory(annotatedType)
                .createInjectionTarget(null);
        CreationalContext context = beanManager.createCreationalContext(null);
        Object instance = injectionTarget.produce(context);
        injectionTarget.inject(instance, context);
        injectionTarget.postConstruct(instance);
        // Record the produced @Dependent test instance + its CDI machinery on
        // this (container-scoped) extension so it is released when the container
        // shuts down. Otherwise this CreationalContext — and the @Dependent
        // objects injected into the test (incl. JDK auto-mocks) — would be
        // abandoned and their @PreDestroy would never run.
        producedTestInstances.add(new ProducedTestInstance(injectionTarget, instance, context));
        return instance;
    }

    /**
     * Release every {@code @Dependent} test instance produced by
     * {@link #instantiate(BeanManager, Class)} when the container shuts
     * down: fire its {@code @PreDestroy}, dispose it, and release its
     * {@link CreationalContext} so every {@code @Dependent} object
     * injected into the test is destroyed (its {@code @PreDestroy} runs
     * too). Best-effort: every instance is released even if one throws;
     * the first failure is rethrown with the rest suppressed.
     */
    void onBeforeShutdown(@Observes BeforeShutdown event) {
        RuntimeException primary = null;
        for (ProducedTestInstance produced : producedTestInstances) {
            try {
                releaseProduced(produced);
            } catch (RuntimeException releaseFailure) {
                if (primary == null) {
                    primary = releaseFailure;
                } else {
                    primary.addSuppressed(releaseFailure);
                }
            }
        }
        producedTestInstances.clear();
        if (primary != null) {
            throw primary;
        }
    }

    @SuppressWarnings("unchecked")
    private static void releaseProduced(ProducedTestInstance produced) {
        produced.injectionTarget.preDestroy(produced.instance);
        produced.injectionTarget.dispose(produced.instance);
        produced.creationalContext.release();
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
            // Deliberately raw: this walk reads field annotations, so an
            // unqualified field yields the empty set and an @Any field
            // yields {@Any}, while onProcessInjectionPoint sees CDI's
            // own normalized view of the very same injection. IpKey
            // reconciles the two - see IpKey#normalize, and issue 155
            // for what happens when only one side normalizes.
            unsatisfiedCandidateIps.add(new IpKey(targetType, qualifiers));
        }
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
     * of non-JDK target types. Invoked once per container from
     * {@code onBeforeBeanDiscovery} into the
     * {@code autoMockNonJdkScope} field, so each test class's
     * {@link ConfigProvider#getConfig()} layer selects the scope
     * rather than a value frozen for the JVM.
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
            this.qualifiers = normalize(qualifiers);
        }

        /**
         * Reduces a qualifier set to the form CDI resolves on, so that
         * two injection points the container would satisfy with one
         * bean also produce one key here.
         *
         * <p>Two rules, and both exist because a collection path once
         * disagreed with the other about the same injection:
         *
         * <ul>
         *   <li>{@code @Any} is dropped. Every bean holds it
         *       implicitly, so it never narrows a resolution - which
         *       makes {@code @Inject @Any Foo} and plain
         *       {@code @Inject Foo} the same request, and two keys a
         *       bug. This is the remainder of issue 155 that was left
         *       out of scope there, and it produced the same
         *       {@code AmbiguousResolutionException} the moment
         *       {@code @Any} was written out on a test-class field.</li>
         *   <li>An otherwise empty set becomes {@code @Default}, which
         *       is what the container reports for an injection point
         *       that declares no qualifier (CDI 4.1, "Default qualifier
         *       at injection points"). The reflective walk over the
         *       test class sees raw annotations and would otherwise
         *       yield the empty set - the original issue 155.</li>
         * </ul>
         *
         * <p>Doing this in the constructor rather than at each call
         * site is the point: the two paths cannot drift apart again.
         *
         * @param qualifiers the qualifiers as collected
         * @return the normalized set, never empty
         */
        private static Set<Annotation> normalize(Set<Annotation> qualifiers) {
            Set<Annotation> normalized = new LinkedHashSet<>();
            for (Annotation qualifier : qualifiers) {
                if (!Any.class.equals(qualifier.annotationType())) {
                    normalized.add(qualifier);
                }
            }
            if (normalized.isEmpty()) {
                normalized.add(Default.Literal.INSTANCE);
            }
            return normalized;
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
                        // deepHashCode, not value.hashCode(), to stay
                        // consistent with the Objects.deepEquals in
                        // qualifiersEquivalent. For an array the plain
                        // hashCode is an identity hash, and an unstable
                        // one: the annotation proxy clones an array
                        // member on every access to keep the annotation
                        // immutable, so two reads of the same member
                        // hash differently.
                        //
                        // No legal qualifier can reach that branch
                        // today - CDI 4.1 makes an array-valued or
                        // annotation-valued member that is not
                        // @Nonbinding a definition error, and the loop
                        // above skips @Nonbinding members - so this is
                        // an invariant guard, not a fix for an
                        // observable failure. It keeps equals and
                        // hashCode consistent on their own terms rather
                        // than by relying on the container to reject
                        // the input first. See issue 158.
                        h = 31 * h + Arrays.deepHashCode(new Object[] {value});
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

    /**
     * A {@code @Dependent} test instance produced for the active
     * container, plus the {@link InjectionTarget} and
     * {@link CreationalContext} that built it, retained so the instance
     * (and its {@code @Dependent} dependents) can be released on
     * container shutdown.
     */
    @SuppressWarnings("rawtypes")
    private static class ProducedTestInstance {

        private final InjectionTarget injectionTarget;
        private final Object instance;
        private final CreationalContext creationalContext;

        private ProducedTestInstance(
                InjectionTarget injectionTarget, Object instance, CreationalContext creationalContext) {
            this.injectionTarget = injectionTarget;
            this.instance = instance;
            this.creationalContext = creationalContext;
        }
    }
}
