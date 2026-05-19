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
package org.os890.jawelte.module.cdi.impl.adapter.quarkus;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.InjectionPointInfo;
import jakarta.enterprise.inject.build.compatible.spi.ObserverInfo;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.AnnotationMember;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.FieldInfo;
import jakarta.enterprise.lang.model.declarations.ParameterInfo;
import jakarta.enterprise.lang.model.types.ClassType;
import jakarta.enterprise.lang.model.types.ParameterizedType;
import jakarta.enterprise.lang.model.types.Type;

/**
 * CDI 4.0 {@link BuildCompatibleExtension} that hosts cdi-module's
 * test-only synthesis features under {@code @QuarkusTest}:
 *
 * <ol>
 *   <li><b>Inline {@code @TestBean} static fields.</b> Every
 *       {@code @TestBean}-annotated static field declared on a class
 *       bean is registered as a synthetic bean of the field's type;
 *       at runtime {@link InlineFieldSyntheticBeanCreator} reads the
 *       field value reflectively.</li>
 *   <li><b>Class-level {@code @TestBean(bean = X.class)}.</b> Every
 *       {@code @Alternative}-tagged class named via {@code bean = X}
 *       on the test class (or any meta-annotation chain, repeatable
 *       included) is registered as a synthetic alternative bean of
 *       all of {@code X}'s bean types with
 *       {@code @Priority(Integer.MAX_VALUE)} so it wins resolution
 *       over the default. {@link TestBeanInstanceSyntheticBeanCreator}
 *       does the {@code new X()} via reflection per invocation.
 *       Non-{@code @Alternative} classes are silently ignored
 *       (matches the standalone-ArC behaviour driven by
 *       {@code AlternativePriorities}).</li>
 *   <li><b>Auto-mock unsatisfied injection points.</b> For every IP
 *       no existing or inline-field bean satisfies, a synthetic
 *       Mockito-mock bean of the IP's required type (with qualifiers)
 *       is registered via {@link MockSyntheticBeanCreator}.
 *       {@code Provider<X>} / {@code Instance<X>} wrappers are
 *       unwrapped to {@code X}.</li>
 * </ol>
 *
 * <p>Replaces the standalone-ArC
 * {@code MockAndInlineBeanRegistrar} / {@code AlternativePriorities}
 * pipeline under {@code @QuarkusTest}: Quarkus owns the build, so we
 * plug in via the CDI-standard build-time SPI rather than via ArC's
 * {@code BeanRegistrar} API.
 *
 * <p>Discovered via
 * {@code META-INF/services/jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension}.
 *
 * <p>Phases:
 * <ul>
 *   <li>{@code @Registration(types = Object.class)} fires once per
 *       bean. We accumulate four sets: bean shapes for satisfaction
 *       matching, injection-point shapes for the auto-mock pass,
 *       {@code @TestBean} static-field records for the inline-field
 *       pass, and class-level {@code @TestBean(bean = X)} target FQNs
 *       for the alternative-activation pass.</li>
 *   <li>{@code @Synthesis} runs three coordinated passes: inline-field
 *       beans, class-level alternative beans, then auto-mocks for
 *       whatever's still uncovered. Each pass updates
 *       {@code existingBeans} so the next pass treats its registrations
 *       as already-satisfied.</li>
 * </ul>
 *
 * <p>Limitations of this first pass (intentional, to be expanded):
 * <ul>
 *   <li>Qualifier matching is by qualifier-FQN set only; nonbinding
 *       members aren't normalised yet.</li>
 *   <li>Parameterised non-wrapper types (e.g. {@code BaseDao<Order>})
 *       are registered as raw bean types.</li>
 *   <li>The default scope for auto-mocked synthetic beans is
 *       hard-coded to {@code @Dependent}. The
 *       {@code BeanScopeMapper}-driven defaults from the
 *       standalone-ArC path are not applied here yet.</li>
 * </ul>
 */
public class JaweltAutoMockBuildCompatibleExtension implements BuildCompatibleExtension {

    private static final String PROVIDER_FQN = "jakarta.inject.Provider";
    private static final String INSTANCE_FQN = "jakarta.enterprise.inject.Instance";
    private static final String TEST_BEAN_FQN = "org.os890.jawelte.core.api.TestBean";
    private static final String TEST_BEANS_FQN = "org.os890.jawelte.core.api.TestBeans";
    private static final String QUALIFIER_FQN = "jakarta.inject.Qualifier";
    private static final String VOID_FQN = "void";
    private static final String NONBINDING_FQN = "jakarta.enterprise.util.Nonbinding";

    private final Set<BeanShape> existingBeans = new LinkedHashSet<>();

    private final Set<UnsatisfiedKey> seenInjectionPoints = new LinkedHashSet<>();

    /**
     * IP-shape → CDI {@link Type} representing the actual type to
     * register as the synthetic mock's bean type. Lets a parameterized
     * IP like {@code BaseDao<Order>} register a synthetic bean of
     * exactly {@code BaseDao<Order>} (which Quarkus's strict synthetic
     * bean type matching requires) rather than the raw {@code BaseDao}.
     */
    private final java.util.Map<UnsatisfiedKey, Type> injectionPointTypes = new java.util.LinkedHashMap<>();

    /**
     * IP-shape → the user-declared qualifier {@link AnnotationInfo}s
     * captured at {@code @Registration}. Preserves qualifier member
     * values (e.g. {@code @Named("primary")}) so the synthetic auto-mock
     * bean registers with exactly the qualifier the IP requires, rather
     * than a default-valued instance of the qualifier annotation type.
     */
    private final java.util.Map<UnsatisfiedKey, Set<AnnotationInfo>> injectionPointQualifiers
            = new java.util.LinkedHashMap<>();

    private final Set<InlineFieldRecord> inlineFields = new LinkedHashSet<>();

    private final Set<String> testBeanTargetFqns = new LinkedHashSet<>();

    private final Set<String> testBeanProducerTargetFqns = new LinkedHashSet<>();

    /** Public no-arg constructor required by {@code ServiceLoader}. */
    public JaweltAutoMockBuildCompatibleExtension() {
    }

    /**
     * Capture the types of every bean, the injection-point types of
     * every bean, every class bean's {@code @TestBean} static fields,
     * and every class-level {@code @TestBean(bean = X)} reachable via
     * the bean's annotation chain (including meta-annotations and
     * {@code @TestBeans} repeatable holders).
     *
     * @param bean the bean being registered
     */
    @Registration(types = Object.class)
    public void collect(BeanInfo bean) {
        Set<String> beanQualifierNames = qualifierFqnSet(bean.qualifiers());
        for (Type beanType : bean.types()) {
            String name = typeName(beanType);
            if (name != null) {
                existingBeans.add(new BeanShape(name, beanQualifierNames));
            }
        }
        for (InjectionPointInfo ip : bean.injectionPoints()) {
            UnsatisfiedKey key = unsatisfiedKeyFor(ip);
            if (key != null) {
                seenInjectionPoints.add(key);
                injectionPointTypes.putIfAbsent(key, effectiveIpType(ip));
                injectionPointQualifiers.putIfAbsent(key, userQualifierAnnotations(ip));
            }
        }
        if (bean.isClassBean()) {
            ClassInfo declaringClass = bean.declaringClass();
            collectInlineFields(declaringClass);
            collectTestBeanTargets(declaringClass);
        }
    }

    /**
     * Treat every observer-method parameter that is not the event
     * parameter as an injection point — CDI declares those parameters
     * to be CDI injection targets, but they are not exposed via
     * {@link BeanInfo#injectionPoints()}.
     *
     * @param observer the observer method being registered
     */
    @Registration(types = Object.class)
    public void collectObserverParameters(ObserverInfo observer) {
        ParameterInfo eventParameter = observer.eventParameter();
        for (ParameterInfo parameter : observer.observerMethod().parameters()) {
            if (parameter == eventParameter) {
                continue;
            }
            String typeFqn = typeName(parameter.type());
            if (typeFqn == null) {
                continue;
            }
            Set<String> qualifierNames = parameterQualifierFqnSet(parameter);
            UnsatisfiedKey key = new UnsatisfiedKey(typeFqn, qualifierNames);
            seenInjectionPoints.add(key);
            injectionPointTypes.putIfAbsent(key, parameter.type());
            injectionPointQualifiers.putIfAbsent(
                    key, parameterQualifierAnnotations(parameter));
        }
    }

    private static Set<String> parameterQualifierFqnSet(ParameterInfo parameter) {
        Set<String> names = new TreeSet<>();
        for (AnnotationInfo annotation : parameter.annotations()) {
            boolean isQualifier = annotation.declaration().hasAnnotation(
                    ann -> QUALIFIER_FQN.equals(ann.name()));
            if (isQualifier && !isBuiltInQualifier(annotation.name())) {
                names.add(annotation.name());
            }
        }
        return names;
    }

    private static Set<AnnotationInfo> parameterQualifierAnnotations(ParameterInfo parameter) {
        Set<AnnotationInfo> result = new LinkedHashSet<>();
        for (AnnotationInfo annotation : parameter.annotations()) {
            boolean isQualifier = annotation.declaration().hasAnnotation(
                    ann -> QUALIFIER_FQN.equals(ann.name()));
            if (isQualifier && !isBuiltInQualifier(annotation.name())) {
                result.add(annotation);
            }
        }
        return result;
    }

    private void collectInlineFields(ClassInfo declaringClass) {
        if (declaringClass == null) {
            return;
        }
        for (FieldInfo field : declaringClass.fields()) {
            if (!hasTestBeanAnnotation(field)) {
                continue;
            }
            if (!Modifier.isStatic(field.modifiers())) {
                // Non-static @TestBean fields are a user error; the
                // standalone-ArC path's collectInlineFields throws
                // on them up front. Skip silently here so the user
                // sees the framework's error rather than a confusing
                // Quarkus build failure.
                continue;
            }
            String typeName = typeName(field.type());
            if (typeName == null) {
                continue;
            }
            Set<String> qualifierNames = annotationQualifierFqnSet(field.annotations());
            inlineFields.add(new InlineFieldRecord(
                    declaringClass.name(),
                    field.name(),
                    typeName,
                    qualifierNames));
        }
    }

    /**
     * Walk the class-level annotation chain (including meta-annotations
     * and {@code @TestBeans} holders) collecting every
     * {@code @TestBean(bean = X)} target FQN. Visit-bookkeeping uses
     * annotation FQN to avoid revisiting the same meta-annotation in a
     * cyclic chain.
     */
    private void collectTestBeanTargets(ClassInfo declaringClass) {
        if (declaringClass == null) {
            return;
        }
        Set<String> visited = new HashSet<>();
        walkAnnotationsForTestBean(declaringClass.annotations(), visited);
    }

    private void walkAnnotationsForTestBean(
            Collection<AnnotationInfo> annotations, Set<String> visited) {
        for (AnnotationInfo annotation : annotations) {
            String annotationName = annotation.name();
            if (TEST_BEAN_FQN.equals(annotationName)) {
                collectFromTestBean(annotation);
                continue;
            }
            if (TEST_BEANS_FQN.equals(annotationName)) {
                if (annotation.hasValue()) {
                    AnnotationMember value = annotation.value();
                    if (value.isArray()) {
                        for (AnnotationMember entry : value.asArray()) {
                            if (entry.isNestedAnnotation()) {
                                collectFromTestBean(entry.asNestedAnnotation());
                            }
                        }
                    }
                }
                continue;
            }
            if (!visited.add(annotationName)) {
                continue;
            }
            if (annotationName.startsWith("java.") || annotationName.startsWith("jakarta.")) {
                continue;
            }
            ClassInfo declaration = annotation.declaration();
            if (declaration == null) {
                continue;
            }
            walkAnnotationsForTestBean(declaration.annotations(), visited);
        }
    }

    private void collectFromTestBean(AnnotationInfo testBeanAnnotation) {
        String beanFqn = readClassMember(testBeanAnnotation, "bean");
        if (beanFqn != null && !VOID_FQN.equals(beanFqn)) {
            testBeanTargetFqns.add(beanFqn);
        }
        String producerFqn = readClassMember(testBeanAnnotation, "beanProducer");
        if (producerFqn != null && !VOID_FQN.equals(producerFqn)) {
            testBeanProducerTargetFqns.add(producerFqn);
        }
    }

    private static String readClassMember(AnnotationInfo annotation, String memberName) {
        if (!annotation.hasMember(memberName)) {
            return null;
        }
        AnnotationMember member = annotation.member(memberName);
        if (!member.isClass()) {
            return null;
        }
        return typeName(member.asType());
    }

    /**
     * Synthesis runs in three passes — inline-field beans, class-level
     * {@code @TestBean(bean=X)} alternatives, then auto-mocks for any
     * remaining unsatisfied IP — each pass primes
     * {@code existingBeans} so later passes see its registrations.
     *
     * @param components the synthesis surface CDI hands the extension
     */
    @Synthesis
    public void registerSynthetics(SyntheticComponents components) {
        registerInlineFieldBeans(components);
        registerTestBeanAlternatives(components);
        registerTestBeanProducerAlternatives(components);
        registerAutoMockBeans(components);
    }

    private void registerInlineFieldBeans(SyntheticComponents components) {
        for (InlineFieldRecord field : inlineFields) {
            Class<?> beanType = loadClass(field.typeName);
            if (beanType == null) {
                continue;
            }
            SyntheticBeanBuilder<Object> builder = components.<Object>addBean(Object.class)
                    .type(beanType)
                    .scope(Dependent.class)
                    .createWith(InlineFieldSyntheticBeanCreator.class)
                    .withParam("declaringClass", field.declaringClass)
                    .withParam("fieldName", field.fieldName);
            applyQualifiers(builder, field.qualifierNames);
            existingBeans.add(new BeanShape(field.typeName, field.qualifierNames));
        }
    }

    private void registerTestBeanAlternatives(SyntheticComponents components) {
        for (String targetFqn : testBeanTargetFqns) {
            Class<?> targetClass = loadClass(targetFqn);
            if (targetClass == null) {
                continue;
            }
            if (!targetClass.isAnnotationPresent(Alternative.class)) {
                // Non-@Alternative classes are silently ignored — same
                // contract as standalone-ArC's AlternativePriorities
                // branch, which only assigns Integer.MAX_VALUE to
                // classes that already carry @Alternative.
                continue;
            }
            registerTestBeanAlternative(components, targetClass);
        }
    }

    /**
     * Helper extracted to capture the target's static type {@code T} so
     * the synthetic bean's implementation class is the @TestBean target
     * itself rather than {@code Object}. Without this capture the
     * client proxy ArC generates for a normal-scoped synthetic bean
     * would subclass {@code Object}, failing the cast to the IP's bean
     * type at injection time.
     */
    private <T> void registerTestBeanAlternative(SyntheticComponents components, Class<T> targetClass) {
        List<Class<?>> beanTypes = computeBeanTypes(targetClass);
        Class<? extends Annotation> scope = resolveScope(targetClass);
        @SuppressWarnings("unchecked")
        Class<? extends jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator<T>> creator =
                (Class<? extends jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator<T>>)
                        (Class<?>) TestBeanInstanceSyntheticBeanCreator.class;
        SyntheticBeanBuilder<T> builder = components.addBean(targetClass)
                .scope(scope)
                .alternative(true)
                .priority(Integer.MAX_VALUE)
                .createWith(creator)
                .withParam("targetClass", targetClass);
        for (Class<?> beanType : beanTypes) {
            builder.type(beanType);
            existingBeans.add(new BeanShape(beanType.getName(), Set.of()));
        }
    }

    private void registerTestBeanProducerAlternatives(SyntheticComponents components) {
        for (String producerFqn : testBeanProducerTargetFqns) {
            Class<?> producerClass = loadClass(producerFqn);
            if (producerClass == null) {
                continue;
            }
            if (!producerClass.isAnnotationPresent(Alternative.class)) {
                // Same silent-skip contract as @TestBean(bean=…) for
                // non-@Alternative classes.
                continue;
            }
            for (Method method : producerClass.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Produces.class)) {
                    continue;
                }
                if (method.getParameterCount() != 0) {
                    // @Produces method injection points are not
                    // representable through synthetic-bean creators;
                    // they need their own injection-point plumbing.
                    // Skip silently for now — the auto-mock pass picks
                    // up any unsatisfied IPs the absent producer
                    // method would have served.
                    continue;
                }
                registerProducerMethodAlternative(components, producerClass, method);
            }
        }
    }

    /**
     * Helper extracted to capture the producer method's return type
     * {@code T} for the synthetic bean's implementation class, mirroring
     * the {@link #registerTestBeanAlternative} pattern.
     */
    private <T> void registerProducerMethodAlternative(
            SyntheticComponents components, Class<?> producerClass, Method method) {
        @SuppressWarnings("unchecked")
        Class<T> returnType = (Class<T>) method.getReturnType();
        if (returnType == void.class) {
            return;
        }
        List<Class<?>> beanTypes = computeBeanTypes(returnType);
        Class<? extends Annotation> scope = resolveScope(method, returnType);
        @SuppressWarnings("unchecked")
        Class<? extends jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator<T>> creator =
                (Class<? extends jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator<T>>)
                        (Class<?>) TestBeanProducerMethodSyntheticBeanCreator.class;
        SyntheticBeanBuilder<T> builder = components.addBean(returnType)
                .scope(scope)
                .alternative(true)
                .priority(Integer.MAX_VALUE)
                .createWith(creator)
                .withParam("producerClass", producerClass.getName())
                .withParam("methodName", method.getName());
        for (Class<?> beanType : beanTypes) {
            builder.type(beanType);
            existingBeans.add(new BeanShape(beanType.getName(), Set.of()));
        }
        applyMethodQualifiers(builder, method);
    }

    private static <T> void applyMethodQualifiers(SyntheticBeanBuilder<T> builder, Method method) {
        for (Annotation annotation : method.getAnnotations()) {
            Class<? extends Annotation> annType = annotation.annotationType();
            if (!annType.isAnnotationPresent(jakarta.inject.Qualifier.class)) {
                continue;
            }
            if (isBuiltInQualifier(annType.getName())) {
                continue;
            }
            builder.qualifier(annType);
        }
    }

    private void registerAutoMockBeans(SyntheticComponents components) {
        for (UnsatisfiedKey ip : seenInjectionPoints) {
            if (existingBeans.contains(new BeanShape(ip.typeName, ip.qualifierNames))) {
                continue;
            }
            if (isBuiltInCdiType(ip.typeName)) {
                continue;
            }
            Class<?> beanType = loadClass(ip.typeName);
            if (beanType == null) {
                continue;
            }
            registerAutoMockBean(components, ip, beanType);
            existingBeans.add(new BeanShape(ip.typeName, ip.qualifierNames));
        }
    }

    /**
     * Helper extracted to capture the target's static type {@code T} so
     * {@code addBean(beanType)} (rather than {@code addBean(Object.class)})
     * fixes the proxy that ArC generates for normal-scoped synthetic
     * beans to subclass the IP's required type. Without this capture
     * the proxy is for {@code Object} and the cast to the IP's bean
     * type fails at injection time.
     *
     * <p>Default scope is {@code @Singleton}: gives one shared mock per
     * IP shape (so multiple IPs with the same type+qualifier — modulo
     * {@code @Nonbinding} members — resolve to the same instance), and
     * avoids the request-context preconditions of {@code @RequestScoped}.
     */
    private <T> void registerAutoMockBean(
            SyntheticComponents components, UnsatisfiedKey ip, Class<T> beanType) {
        @SuppressWarnings("unchecked")
        Class<? extends jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator<T>> creator =
                (Class<? extends jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator<T>>)
                        (Class<?>) MockSyntheticBeanCreator.class;
        // Default scope mirrors the standalone-ArC
        // MockAndInlineBeanRegistrar:
        // - @Dependent for JDK types (sharing a stateful collection
        //   / map across IPs would be surprising)
        // - @RequestScoped for user types (shared per request — one
        //   mock-and-verify cycle per test method)
        Class<? extends Annotation> autoMockScope = isJdkType(ip.typeName)
                ? Dependent.class
                : jakarta.enterprise.context.RequestScoped.class;
        SyntheticBeanBuilder<T> builder = components.addBean(beanType)
                .scope(autoMockScope)
                .createWith(creator)
                .withParam("targetType", beanType);
        Type cdiIpType = injectionPointTypes.get(ip);
        if (cdiIpType != null && cdiIpType.isParameterizedType()) {
            // Match parameterized IPs (e.g. BaseDao<Order>) with a
            // synthetic bean of exactly that parameterized type —
            // ArC's bean-resolution treats raw and parameterized bean
            // types differently and a raw BaseDao bean won't satisfy
            // a BaseDao<Order> IP.
            builder.type(cdiIpType);
        } else {
            builder.type(beanType);
        }
        Set<AnnotationInfo> capturedQualifiers = injectionPointQualifiers.get(ip);
        if (capturedQualifiers != null && !capturedQualifiers.isEmpty()) {
            // Preserve qualifier member values (e.g. @Named("primary"))
            // by passing the captured AnnotationInfo through. Fall
            // back to Class-based registration when the qualifier has
            // no binding members (e.g. @ConfigKey with @Nonbinding
            // name): Quarkus skips the annotation-literal-class
            // generation for AnnotationInfo-supplied qualifiers on
            // synthetic beans whose target type is a JDK class (the
            // synthetic_Bean class compiles fine but boot fails with
            // NoClassDefFoundError because the literal class never
            // gets generated). Class-based registration goes through
            // a different code path that does emit the literal.
            for (AnnotationInfo qualifier : capturedQualifiers) {
                if (hasOnlyNonbindingMembers(qualifier)) {
                    Class<?> qualifierClass = loadClass(qualifier.name());
                    if (qualifierClass != null
                            && java.lang.annotation.Annotation.class.isAssignableFrom(qualifierClass)) {
                        @SuppressWarnings("unchecked")
                        Class<? extends java.lang.annotation.Annotation> qualifierAnnotationType =
                                (Class<? extends java.lang.annotation.Annotation>) qualifierClass;
                        builder.qualifier(qualifierAnnotationType);
                        continue;
                    }
                }
                builder.qualifier(qualifier);
            }
        } else {
            applyQualifiers(builder, ip.qualifierNames);
        }
    }

    /**
     * Whether every declared member of the qualifier annotation is
     * meta-annotated with {@code @Nonbinding}. When true, the
     * qualifier instance's member values are irrelevant for CDI
     * resolution, so we can register the qualifier by type
     * ({@code builder.qualifier(Class)}) and dodge the
     * annotation-literal-class generation gap that strikes
     * {@code builder.qualifier(AnnotationInfo)} on JDK-target
     * synthetic beans.
     */
    private static boolean hasOnlyNonbindingMembers(AnnotationInfo qualifier) {
        ClassInfo declaration = qualifier.declaration();
        if (declaration == null) {
            return false;
        }
        Collection<jakarta.enterprise.lang.model.declarations.MethodInfo> methods =
                declaration.methods();
        if (methods.isEmpty()) {
            return true;
        }
        for (jakarta.enterprise.lang.model.declarations.MethodInfo method : methods) {
            if (!method.hasAnnotation(ann -> NONBINDING_FQN.equals(ann.name()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compute the set of bean types a class exposes per CDI 4.0 §4.2:
     * the class itself, every superclass (excluding {@code Object}),
     * and every implemented interface (transitively). Returned in
     * insertion order so the class type is registered first.
     */
    private static List<Class<?>> computeBeanTypes(Class<?> targetClass) {
        Set<Class<?>> visited = new LinkedHashSet<>();
        addBeanType(targetClass, visited);
        for (Class<?> c = targetClass.getSuperclass(); c != null && c != Object.class; c = c.getSuperclass()) {
            addBeanType(c, visited);
        }
        collectInterfaces(targetClass, visited);
        return List.copyOf(visited);
    }

    private static void addBeanType(Class<?> beanType, Set<Class<?>> visited) {
        if (beanType == null || beanType == Object.class) {
            return;
        }
        visited.add(beanType);
    }

    private static void collectInterfaces(Class<?> clazz, Set<Class<?>> visited) {
        if (clazz == null) {
            return;
        }
        for (Class<?> iface : clazz.getInterfaces()) {
            if (visited.add(iface)) {
                collectInterfaces(iface, visited);
            }
        }
        collectInterfaces(clazz.getSuperclass(), visited);
    }

    /**
     * Resolve the scope to use for a synthetic alternative bean
     * mirroring class {@code targetClass}. Prefers an explicit CDI
     * scope on the class; falls back to {@code @Dependent} when none
     * is declared (matching the standalone-ArC transformation that
     * adds {@code @Dependent} to non-scoped {@code @Alternative}
     * classes).
     */
    private static Class<? extends Annotation> resolveScope(Class<?> targetClass) {
        for (Annotation annotation : targetClass.getAnnotations()) {
            Class<? extends Annotation> annType = annotation.annotationType();
            String fqn = annType.getName();
            if (isCdiScopeAnnotation(fqn)) {
                return annType;
            }
        }
        return Dependent.class;
    }

    /**
     * Resolve the scope for a {@code @Produces} method's synthetic
     * bean: prefer a CDI scope annotation on the method itself, then
     * one on the method's return type, defaulting to {@code @Dependent}
     * (CDI's contract for producer methods without a declared scope).
     */
    private static Class<? extends Annotation> resolveScope(Method method, Class<?> returnType) {
        for (Annotation annotation : method.getAnnotations()) {
            Class<? extends Annotation> annType = annotation.annotationType();
            if (isCdiScopeAnnotation(annType.getName())) {
                return annType;
            }
        }
        return resolveScope(returnType);
    }

    private static boolean isCdiScopeAnnotation(String fqn) {
        return "jakarta.enterprise.context.ApplicationScoped".equals(fqn)
                || "jakarta.enterprise.context.RequestScoped".equals(fqn)
                || "jakarta.enterprise.context.SessionScoped".equals(fqn)
                || "jakarta.enterprise.context.ConversationScoped".equals(fqn)
                || "jakarta.enterprise.context.Dependent".equals(fqn)
                || "jakarta.inject.Singleton".equals(fqn);
    }

    private static Class<?> loadClass(String fqn) {
        try {
            return Class.forName(fqn, false,
                    Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException missing) {
            return null;
        }
    }

    private static void applyQualifiers(
            SyntheticBeanBuilder<?> builder, Set<String> qualifierFqns) {
        for (String qualifierFqn : qualifierFqns) {
            if (isBuiltInQualifier(qualifierFqn)) {
                continue;
            }
            Class<?> qualifierClass = loadClass(qualifierFqn);
            if (qualifierClass == null) {
                continue;
            }
            if (java.lang.annotation.Annotation.class.isAssignableFrom(qualifierClass)) {
                @SuppressWarnings("unchecked")
                Class<? extends java.lang.annotation.Annotation> typedQualifier =
                        (Class<? extends java.lang.annotation.Annotation>) qualifierClass;
                builder.qualifier(typedQualifier);
            }
        }
    }

    private static boolean hasTestBeanAnnotation(FieldInfo field) {
        for (AnnotationInfo annotation : field.annotations()) {
            if (TEST_BEAN_FQN.equals(annotation.name())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBuiltInQualifier(String qualifierFqn) {
        return "jakarta.enterprise.inject.Default".equals(qualifierFqn)
                || "jakarta.enterprise.inject.Any".equals(qualifierFqn);
    }

    /**
     * Per-qualifier signatures that include each qualifier's binding
     * member values (anything not marked {@code @Nonbinding}).
     * {@code @ServiceType("express")} and {@code @ServiceType("standard")}
     * yield distinct signatures and therefore distinct synthetic mock
     * beans, while {@code @DataSource(name="primary")} and
     * {@code @DataSource(name="secondary")} (where {@code name} is
     * {@code @Nonbinding}) collapse to the same signature and share
     * one mock.
     *
     * <p>Drops the implicit {@code @Default} / {@code @Any} so a bean
     * tagged {@code [Default, Any]} still matches an IP with
     * {@code [Default]}.
     */
    private static Set<String> qualifierFqnSet(Collection<AnnotationInfo> qualifiers) {
        Set<String> signatures = new TreeSet<>();
        for (AnnotationInfo qualifier : qualifiers) {
            String name = qualifier.name();
            if (isBuiltInQualifier(name)) {
                continue;
            }
            signatures.add(qualifierBindingSignature(qualifier));
        }
        return signatures;
    }

    /**
     * Encode a qualifier annotation as {@code FQN{m1=v1,m2=v2}} where
     * each {@code m_i} is a binding member (no {@code @Nonbinding}
     * meta-annotation on the declaring method) with the value the
     * caller passed.
     */
    private static String qualifierBindingSignature(AnnotationInfo qualifier) {
        StringBuilder out = new StringBuilder(qualifier.name());
        out.append('{');
        boolean first = true;
        for (java.util.Map.Entry<String, AnnotationMember> entry
                : new TreeMap<>(qualifier.members()).entrySet()) {
            String memberName = entry.getKey();
            if (isNonbindingMember(qualifier, memberName)) {
                continue;
            }
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(memberName).append('=').append(renderMember(entry.getValue()));
        }
        out.append('}');
        return out.toString();
    }

    private static boolean isNonbindingMember(AnnotationInfo qualifier, String memberName) {
        ClassInfo declaration = qualifier.declaration();
        if (declaration == null) {
            return false;
        }
        for (jakarta.enterprise.lang.model.declarations.MethodInfo method : declaration.methods()) {
            if (memberName.equals(method.name())) {
                return method.hasAnnotation(ann -> NONBINDING_FQN.equals(ann.name()));
            }
        }
        return false;
    }

    private static String renderMember(AnnotationMember member) {
        if (member.isString()) {
            return '"' + member.asString() + '"';
        }
        if (member.isBoolean()) {
            return Boolean.toString(member.asBoolean());
        }
        if (member.isInt()) {
            return Integer.toString(member.asInt());
        }
        if (member.isLong()) {
            return Long.toString(member.asLong());
        }
        if (member.isEnum()) {
            return member.asEnumConstant();
        }
        if (member.isClass()) {
            return typeName(member.asType());
        }
        // Fallback: rely on the platform's default representation; sufficient
        // for the qualifier-signature comparison, since equal values always
        // render to equal strings.
        return String.valueOf(member);
    }

    /**
     * Filter a field's full annotation set down to the qualifier
     * annotations among them (those meta-annotated with
     * {@code @Qualifier}).
     */
    private static Set<String> annotationQualifierFqnSet(Collection<AnnotationInfo> annotations) {
        Set<String> names = new TreeSet<>();
        for (AnnotationInfo annotation : annotations) {
            boolean isQualifier = annotation.declaration().hasAnnotation(
                    ann -> QUALIFIER_FQN.equals(ann.name()));
            if (isQualifier && !isBuiltInQualifier(annotation.name())) {
                names.add(annotation.name());
            }
        }
        return names;
    }

    private static UnsatisfiedKey unsatisfiedKeyFor(InjectionPointInfo ip) {
        Type ipType = ip.type();
        Set<String> qualifierNames = qualifierFqnSet(ip.qualifiers());
        String unwrapped = unwrapWrapperType(ipType);
        if (unwrapped != null) {
            return new UnsatisfiedKey(unwrapped, qualifierNames);
        }
        String raw = typeName(ipType);
        if (raw == null) {
            return null;
        }
        return new UnsatisfiedKey(raw, qualifierNames);
    }

    /**
     * Capture the IP's user-declared qualifier annotations (excluding
     * the implicit {@code @Default} / {@code @Any}) so the synthetic
     * auto-mock bean can register them with their member values
     * intact.
     */
    private static Set<AnnotationInfo> userQualifierAnnotations(InjectionPointInfo ip) {
        Set<AnnotationInfo> result = new LinkedHashSet<>();
        for (AnnotationInfo qualifier : ip.qualifiers()) {
            if (isBuiltInQualifier(qualifier.name())) {
                continue;
            }
            result.add(qualifier);
        }
        return result;
    }

    /**
     * The {@code Type} a synthetic auto-mock bean should advertise for
     * this injection point — the wrapper-unwrapped argument type for
     * {@code Provider<X>} / {@code Instance<X>}, otherwise the IP's
     * raw type.
     */
    private static Type effectiveIpType(InjectionPointInfo ip) {
        Type ipType = ip.type();
        if (ipType.isParameterizedType()) {
            ParameterizedType parameterized = ipType.asParameterizedType();
            String wrapperName = parameterized.declaration().name();
            if ((PROVIDER_FQN.equals(wrapperName) || INSTANCE_FQN.equals(wrapperName))
                    && parameterized.typeArguments().size() == 1) {
                return parameterized.typeArguments().get(0);
            }
        }
        return ipType;
    }

    /**
     * For {@code Provider<X>} / {@code Instance<X>} IPs, return the
     * FQN of {@code X}. ArC's built-in Provider/Instance handling
     * fulfils the wrapper from a bean for {@code X}, so we only need
     * to register the inner type.
     *
     * @return the inner-type FQN, or {@code null} when the IP type is
     *         not a wrapper
     */
    private static String unwrapWrapperType(Type ipType) {
        if (!ipType.isParameterizedType()) {
            return null;
        }
        ParameterizedType parameterized = ipType.asParameterizedType();
        String wrapperName = parameterized.declaration().name();
        if (!PROVIDER_FQN.equals(wrapperName) && !INSTANCE_FQN.equals(wrapperName)) {
            return null;
        }
        if (parameterized.typeArguments().size() != 1) {
            return null;
        }
        Type arg = parameterized.typeArguments().get(0);
        return typeName(arg);
    }

    private static String typeName(Type type) {
        if (type.isClass()) {
            ClassType classType = type.asClass();
            return classType.declaration().name();
        }
        if (type.isParameterizedType()) {
            ParameterizedType parameterized = type.asParameterizedType();
            return parameterized.declaration().name();
        }
        if (type.isVoid()) {
            return VOID_FQN;
        }
        return null;
    }

    private static boolean isBuiltInCdiType(String typeName) {
        return typeName.startsWith("jakarta.enterprise.")
                || typeName.startsWith("jakarta.inject.")
                || typeName.startsWith("io.quarkus.arc.")
                || "java.lang.Object".equals(typeName);
    }

    /**
     * Whether the given FQN names a JDK type. JDK auto-mocks default
     * to {@code @Dependent} (per-IP fresh instance) rather than the
     * {@code @Singleton} used for user types — sharing a stateful
     * JDK collection / map across IPs would be surprising.
     */
    private static boolean isJdkType(String typeName) {
        return typeName.startsWith("java.") || typeName.startsWith("javax.");
    }

    /**
     * Key identifying an unsatisfied {@code @Inject} point by the
     * target type's FQN plus the FQNs of its qualifier annotations
     * (excluding {@code @Default} / {@code @Any} which are implicit).
     *
     * @param typeName       FQN of the IP's required type (already
     *                       unwrapped for {@code Provider} /
     *                       {@code Instance})
     * @param qualifierNames sorted FQNs of the IP's qualifiers; an
     *                       empty set means "uses {@code @Default}"
     */
    private record UnsatisfiedKey(String typeName, Set<String> qualifierNames) {
    }

    /**
     * Shape of an existing bean as seen during {@code @Registration}:
     * one type FQN paired with the user-declared qualifier-FQN set.
     *
     * @param typeName       FQN of one of the bean's bean types
     * @param qualifierNames the bean's qualifier FQNs (excluding the
     *                       implicit {@code @Default} / {@code @Any})
     */
    private record BeanShape(String typeName, Set<String> qualifierNames) {
    }

    /**
     * Captured {@code @TestBean} static-field declaration used to
     * thread information between {@code @Registration} and
     * {@code @Synthesis}.
     *
     * @param declaringClass FQN of the class declaring the field
     * @param fieldName      the field's name
     * @param typeName       FQN of the field's declared type
     * @param qualifierNames FQNs of user-declared qualifiers on the
     *                       field (excluding the implicit ones)
     */
    private record InlineFieldRecord(
            String declaringClass,
            String fieldName,
            String typeName,
            Set<String> qualifierNames) {
    }
}
