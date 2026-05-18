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
import jakarta.enterprise.lang.model.types.ClassType;
import jakarta.enterprise.lang.model.types.ParameterizedType;
import jakarta.enterprise.lang.model.types.Type;

/**
 * CDI 4.0 {@link BuildCompatibleExtension} that auto-mocks every
 * injection point left unsatisfied by the rest of the bean archive.
 * Replaces the standalone-ArC {@code MockAndInlineBeanRegistrar}
 * under {@code @QuarkusTest}: Quarkus owns the build, so we plug in
 * via the CDI-standard build-time SPI rather than via ArC's
 * {@code BeanRegistrar} API.
 *
 * <p>Discovered via
 * {@code META-INF/services/jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension}.
 *
 * <p>Phases used:
 * <ul>
 *   <li><b>{@link Registration @Registration(types = Object.class)}</b>:
 *       fires once per registered bean. We accumulate the set of
 *       bean type FQNs (so the synthesis phase can decide which IPs
 *       are unsatisfied) and the set of injection points across all
 *       beans (so we know which (type, qualifiers) pairs need mocks).</li>
 *   <li><b>{@link Synthesis @Synthesis}</b>: for every collected IP
 *       whose target type has no matching bean, register a synthetic
 *       Mockito-mock bean for it. {@code Provider<X>} / {@code Instance<X>}
 *       wrapper IPs are unwrapped to {@code X}; ArC's own
 *       Provider / Instance wrappers serve the wrapper part.</li>
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

    private final Set<BeanShape> existingBeans = new LinkedHashSet<>();

    private final Set<UnsatisfiedKey> seenInjectionPoints = new LinkedHashSet<>();

    /** Public no-arg constructor required by {@code ServiceLoader}. */
    public JaweltAutoMockBuildCompatibleExtension() {
    }

    /**
     * Capture the types of every bean and the injection-point types
     * of every bean. {@code types = Object.class} matches any bean
     * type; CDI fires this once per bean.
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
    }

    /**
     * After all beans are registered, walk the collected IPs and
     * register a synthetic Mockito-mock bean for every (type, qualifiers)
     * pair that no existing bean covers.
     *
     * @param components the synthesis surface CDI hands the extension
     */
    @Synthesis
    public void registerSyntheticMocks(SyntheticComponents components) {
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
            for (String qualifierFqn : ip.qualifierNames) {
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
    }

    private static boolean isBuiltInQualifier(String qualifierFqn) {
        return "jakarta.enterprise.inject.Default".equals(qualifierFqn)
                || "jakarta.enterprise.inject.Any".equals(qualifierFqn);
    }

    private static Set<String> qualifierFqnSet(java.util.Collection<AnnotationInfo> qualifiers) {
        Set<String> names = new TreeSet<>();
        for (AnnotationInfo qualifier : qualifiers) {
            names.add(qualifier.name());
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
     * one type FQN paired with the full qualifier-FQN set.
     *
     * @param typeName       FQN of one of the bean's bean types
     * @param qualifierNames the bean's qualifier FQNs
     */
    private record BeanShape(String typeName, Set<String> qualifierNames) {
    }
}
