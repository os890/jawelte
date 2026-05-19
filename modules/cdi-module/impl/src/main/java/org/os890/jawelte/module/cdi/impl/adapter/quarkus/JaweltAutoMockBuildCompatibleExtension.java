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
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.InjectionPointInfo;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.AnnotationMember;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.FieldInfo;
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

    private final Set<BeanShape> existingBeans = new LinkedHashSet<>();

    private final Set<UnsatisfiedKey> seenInjectionPoints = new LinkedHashSet<>();

    private final Set<InlineFieldRecord> inlineFields = new LinkedHashSet<>();

    private final Set<String> testBeanTargetFqns = new LinkedHashSet<>();

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
            }
        }
        if (bean.isClassBean()) {
            ClassInfo declaringClass = bean.declaringClass();
            collectInlineFields(declaringClass);
            collectTestBeanTargets(declaringClass);
        }
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
            SyntheticBeanBuilder<Object> builder = components.<Object>addBean(Object.class)
                    .type(beanType)
                    .scope(Dependent.class)
                    .createWith(MockSyntheticBeanCreator.class)
                    .withParam("targetType", beanType);
            applyQualifiers(builder, ip.qualifierNames);
            existingBeans.add(new BeanShape(ip.typeName, ip.qualifierNames));
        }
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
            SyntheticBeanBuilder<Object> builder, Set<String> qualifierFqns) {
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
     * Filter a bean's qualifier set down to user-declared FQNs.
     * Drops the implicit {@code @Default} / {@code @Any} that CDI
     * tags every bean with so a real bean with {@code [Default, Any]}
     * still matches an IP with {@code [Default]}.
     */
    private static Set<String> qualifierFqnSet(Collection<AnnotationInfo> qualifiers) {
        Set<String> names = new TreeSet<>();
        for (AnnotationInfo qualifier : qualifiers) {
            String name = qualifier.name();
            if (isBuiltInQualifier(name)) {
                continue;
            }
            names.add(name);
        }
        return names;
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
