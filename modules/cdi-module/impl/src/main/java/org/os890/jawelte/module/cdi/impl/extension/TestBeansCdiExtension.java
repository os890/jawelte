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
package org.os890.jawelte.module.cdi.impl.extension;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.inject.Instance;
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
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.api.port.ExcludedPackageFilter;
import org.os890.jawelte.module.cdi.api.port.WhitelistFilter;
import org.os890.jawelte.module.cdi.impl.util.MockitoMockFactory;
import org.os890.jawelte.module.cdi.impl.util.SyntheticBeanUtil;
import org.os890.jawelte.module.cdi.impl.util.TestBeanScanner;

/**
 * CDI Extension shipped by cdi-module. Discovers {@code @TestBean}
 * declarations on the active test class, registers
 * {@code @Alternative} and synthetic beans, applies the
 * {@code limitToTestBeans=true} whitelist veto when in scope, and
 * synthesises Mockito mocks for unsatisfied injection points.
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

    private TestContext activeContext;
    private TestBeanScanner.Result scanResult;
    private boolean limitToTestBeans;
    private WhitelistFilter whitelistFilter;
    private ExcludedPackageFilter excludedPackageFilter;
    private final Map<Type, Set<Annotation>> unsatisfiedCandidateIps = new HashMap<>();

    /** No-arg constructor required by the CDI runtime. */
    public TestBeansCdiExtension() {
    }

    void onBeforeBeanDiscovery(@jakarta.enterprise.event.Observes BeforeBeanDiscovery event) {
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

    private static void forceDiscoveryWithDependentFallback(BeforeBeanDiscovery event, Class<?> target) {
        if (hasBeanDefiningAnnotation(target)) {
            return;
        }
        if (!target.isAnnotationPresent(jakarta.enterprise.inject.Alternative.class)) {
            // Spec: @TestBean(bean=X) where X has no @Alternative is a
            // silent no-op (per scenario 35). Don't promote it to a
            // @Dependent bean.
            return;
        }
        event.addAnnotatedType(target, target.getName())
                .add(jakarta.enterprise.context.Dependent.Literal.INSTANCE);
    }

    private static boolean hasBeanDefiningAnnotation(Class<?> target) {
        for (Annotation a : target.getAnnotations()) {
            Class<? extends Annotation> at = a.annotationType();
            if (at.isAnnotationPresent(jakarta.enterprise.context.NormalScope.class)) {
                return true;
            }
            if (at.isAnnotationPresent(jakarta.inject.Scope.class)) {
                return true;
            }
            if (at.isAnnotationPresent(jakarta.enterprise.inject.Stereotype.class)) {
                return true;
            }
            if (at.equals(jakarta.enterprise.context.Dependent.class)
                    || at.equals(jakarta.inject.Singleton.class)) {
                return true;
            }
        }
        return false;
    }

    void onProcessAnnotatedType(@jakarta.enterprise.event.Observes ProcessAnnotatedType<?> event) {
        if (!limitToTestBeans) {
            return;
        }
        Class<?> rawType = event.getAnnotatedType().getJavaClass();
        if (whitelistFilter != null && !whitelistFilter.isAllowed(rawType)) {
            event.veto();
        }
    }

    <T, X> void onProcessInjectionPoint(@jakarta.enterprise.event.Observes ProcessInjectionPoint<T, X> event) {
        InjectionPoint ip = event.getInjectionPoint();
        Type ipType = ip.getType();
        Set<Annotation> qualifiers = ip.getQualifiers();
        Type targetType = unwrapWrapper(ipType);
        if (targetType == null) {
            return;
        }
        unsatisfiedCandidateIps.merge(
                targetType,
                new LinkedHashSet<>(qualifiers),
                TestBeansCdiExtension::mergeQualifiers);
    }

    private static Set<Annotation> mergeQualifiers(Set<Annotation> existing, Set<Annotation> additional) {
        for (Annotation candidate : additional) {
            boolean alreadyHasSameType = existing.stream()
                    .anyMatch(present -> present.annotationType().equals(candidate.annotationType()));
            if (!alreadyHasSameType) {
                existing.add(candidate);
            }
        }
        return existing;
    }

    void onAfterTypeDiscovery(@jakarta.enterprise.event.Observes AfterTypeDiscovery event) {
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
            @jakarta.enterprise.event.Observes AfterBeanDiscovery event,
            BeanManager beanManager) {
        if (scanResult == null) {
            return;
        }

        // Static-field synthetic beans
        for (TestBeanScanner.StaticField staticField : scanResult.staticFields()) {
            Field field = staticField.field();
            Set<Annotation> qualifiers = collectFieldQualifiers(field);
            SyntheticBeanUtil.registerStaticFieldBean(
                    event, field.getType(), staticField.value(), qualifiers);
        }

        if (limitToTestBeans) {
            // Auto-mocking is disabled in whitelist mode. CDI's own
            // deployment validation surfaces unsatisfied IPs.
            return;
        }

        // Collect IPs from the test class itself (not a CDI bean,
        // therefore no ProcessInjectionPoint events fire for it).
        addTestClassInjectionPoints(beanManager);

        // Synthesise mocks for unsatisfied collected IPs.
        for (Map.Entry<Type, Set<Annotation>> entry : unsatisfiedCandidateIps.entrySet()) {
            Type targetType = entry.getKey();
            Class<?> rawType = rawClassOf(targetType);
            if (rawType == null) {
                continue;
            }
            Set<Annotation> qualifiers = entry.getValue();
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
            Object mockSample = MockitoMockFactory.create(rawType);
            if (mockSample == null) {
                continue;
            }
            // Supplier indirection so each bean lookup gets a fresh mock
            // per @RequestScoped activation.
            SyntheticBeanUtil.registerAutoMockBean(
                    event, rawType, targetType, qualifiers,
                    () -> MockitoMockFactory.create(rawType));
        }
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
            unsatisfiedCandidateIps.merge(
                    targetType,
                    qualifiers,
                    TestBeansCdiExtension::mergeQualifiers);
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
            if (annotation.annotationType()
                    .isAnnotationPresent(jakarta.inject.Qualifier.class)) {
                qualifiers.add(annotation);
            }
        }
        return Collections.unmodifiableSet(qualifiers);
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
     * Test-only accessor exposed for tests that need to inspect what
     * the Extension collected during bootstrap. Production users
     * should not depend on this surface.
     *
     * @return the collected unsatisfied-IP candidates (target type → qualifiers)
     */
    Map<Type, Set<Annotation>> unsatisfiedCandidateIpsForTests() {
        return new HashMap<>(unsatisfiedCandidateIps);
    }
}
