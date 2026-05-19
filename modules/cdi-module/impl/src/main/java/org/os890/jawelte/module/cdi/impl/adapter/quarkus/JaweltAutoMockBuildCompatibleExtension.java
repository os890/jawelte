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

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.InjectionPointInfo;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.FieldInfo;
import jakarta.enterprise.lang.model.types.ClassType;
import jakarta.enterprise.lang.model.types.ParameterizedType;
import jakarta.enterprise.lang.model.types.Type;

/**
 * CDI 4.0 {@link BuildCompatibleExtension} that hosts cdi-module's
 * two test-only synthesis features under {@code @QuarkusTest}:
 *
 * <ol>
 *   <li><b>Inline {@code @TestBean} static fields.</b> Every
 *       {@code @TestBean}-annotated static field declared on a class
 *       bean is registered as a synthetic bean of the field's type;
 *       at runtime {@link InlineFieldSyntheticBeanCreator} reads the
 *       field value reflectively.</li>
 *   <li><b>Auto-mock unsatisfied injection points.</b> For every IP
 *       no existing or inline-field bean satisfies, a synthetic
 *       Mockito-mock bean of the IP's required type (with qualifiers)
 *       is registered via {@link MockSyntheticBeanCreator}.
 *       {@code Provider<X>} / {@code Instance<X>} wrappers are
 *       unwrapped to {@code X}.</li>
 * </ol>
 *
 * <p>Replaces the standalone-ArC {@code MockAndInlineBeanRegistrar}
 * under {@code @QuarkusTest}: Quarkus owns the build, so we plug in
 * via the CDI-standard build-time SPI rather than via ArC's
 * {@code BeanRegistrar} API.
 *
 * <p>Discovered via
 * {@code META-INF/services/jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension}.
 *
 * <p>Phases:
 * <ul>
 *   <li>{@code @Registration(types = Object.class)} fires once per
 *       bean. We accumulate three sets: bean shapes (type +
 *       qualifiers) for satisfaction matching, injection-point
 *       shapes for the auto-mock pass, and {@code @TestBean} static
 *       field records for the inline-field pass.</li>
 *   <li>{@code @Synthesis} first registers all inline-field beans —
 *       so the auto-mock walk that follows can treat them as
 *       already-existing — then registers auto-mocks for whatever's
 *       still uncovered.</li>
 * </ul>
 *
 * <p>Limitations of this first pass (intentional, to be expanded):
 * <ul>
 *   <li>Qualifier matching is by qualifier-FQN set only; nonbinding
 *       members aren't normalised yet.</li>
 *   <li>Parameterised non-wrapper types (e.g. {@code BaseDao<Order>})
 *       are registered as raw bean types.</li>
 *   <li>The scope is hard-coded to {@code @Dependent}. JDK-vs-user
 *       split and the {@code BeanScopeMapper}-driven defaults from
 *       the standalone-ArC path are not applied here yet.</li>
 * </ul>
 */
public class JaweltAutoMockBuildCompatibleExtension implements BuildCompatibleExtension {

    private static final String PROVIDER_FQN = "jakarta.inject.Provider";
    private static final String INSTANCE_FQN = "jakarta.enterprise.inject.Instance";
    private static final String TEST_BEAN_FQN = "org.os890.jawelte.core.api.TestBean";
    private static final String QUALIFIER_FQN = "jakarta.inject.Qualifier";

    private final Set<BeanShape> existingBeans = new LinkedHashSet<>();

    private final Set<UnsatisfiedKey> seenInjectionPoints = new LinkedHashSet<>();

    private final Set<InlineFieldRecord> inlineFields = new LinkedHashSet<>();

    /** Public no-arg constructor required by {@code ServiceLoader}. */
    public JaweltAutoMockBuildCompatibleExtension() {
    }

    /**
     * Capture the types of every bean, the injection-point types of
     * every bean, and every class bean's {@code @TestBean} static
     * fields.
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
            collectInlineFields(bean.declaringClass());
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
     * Synthesis runs in two passes: inline-field beans first (so
     * their types light up the existing-beans set for the auto-mock
     * decision), then auto-mocks for whatever's still uncovered.
     *
     * @param components the synthesis surface CDI hands the extension
     */
    @Synthesis
    public void registerSynthetics(SyntheticComponents components) {
        for (InlineFieldRecord field : inlineFields) {
            Class<?> beanType;
            try {
                beanType = Class.forName(field.typeName, false,
                        Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException missing) {
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
        for (UnsatisfiedKey ip : seenInjectionPoints) {
            if (existingBeans.contains(new BeanShape(ip.typeName, ip.qualifierNames))) {
                continue;
            }
            if (isBuiltInCdiType(ip.typeName)) {
                continue;
            }
            Class<?> beanType;
            try {
                beanType = Class.forName(ip.typeName, false,
                        Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException missing) {
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

    private static void applyQualifiers(
            SyntheticBeanBuilder<Object> builder, Set<String> qualifierFqns) {
        for (String qualifierFqn : qualifierFqns) {
            if (isBuiltInQualifier(qualifierFqn)) {
                continue;
            }
            Class<?> qualifierClass;
            try {
                qualifierClass = Class.forName(qualifierFqn, false,
                        Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException missing) {
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
