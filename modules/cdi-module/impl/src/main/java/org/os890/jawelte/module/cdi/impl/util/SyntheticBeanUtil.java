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
package org.os890.jawelte.module.cdi.impl.util;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.inject.Singleton;

/**
 * Builds CDI synthetic beans for {@code @TestBean} static-field
 * values and for auto-mocked types. Wraps the {@link AfterBeanDiscovery}
 * synthetic-bean configuration API in declarative builder calls.
 */
public abstract class SyntheticBeanUtil {

    /**
     * Suppressed-instantiation constructor. The class is
     * {@code abstract} so direct {@code new} is impossible; the
     * explicit declaration silences {@code javadoc -doclint:all} on
     * the otherwise synthesized default constructor.
     */
    protected SyntheticBeanUtil() {
    }

    /**
     * Register a {@code @Singleton}-scoped synthetic bean whose
     * instance is the value held by a {@code @TestBean} static field.
     *
     * @param event           the {@code AfterBeanDiscovery} event
     * @param fieldType       the declared type of the field
     * @param fieldValue      the field value (the bean instance)
     * @param qualifiers      qualifier annotations from the field plus
     *                        defaults; passed through verbatim
     */
    public static void registerStaticFieldBean(
            AfterBeanDiscovery event,
            Class<?> fieldType,
            Object fieldValue,
            Set<Annotation> qualifiers) {
        Set<Annotation> finalQualifiers = qualifiersWithDefaults(qualifiers);
        Set<java.lang.reflect.Type> types = beanTypes(fieldType);
        event.addBean()
                .beanClass(fieldType)
                .scope(Singleton.class)
                .types(types)
                .qualifiers(finalQualifiers)
                .produceWith(inst -> fieldValue);
    }

    /**
     * Register an auto-mock synthetic bean for an unsatisfied
     * injection-point type. JDK types (everything in {@code java.}
     * packages) get {@link Dependent} scope because the CDI normal-scope
     * proxy does not work on {@code final} JDK classes; everything
     * else gets {@link RequestScoped}.
     *
     * @param event       the {@code AfterBeanDiscovery} event
     * @param rawType     the unsatisfied injection-point raw type
     *                    (used as {@code beanClass} and as the input
     *                    to the JDK-type / scope decision)
     * @param targetType  the full bean type to register, including
     *                    parameterization (e.g. {@code List<String>})
     * @param qualifiers  the injection-point qualifiers (defaults
     *                    auto-completed where missing)
     * @param mockSupplier supplier that creates a fresh Mockito mock
     *                    on each invocation; called once per bean
     *                    instance lifecycle
     */
    public static void registerAutoMockBean(
            AfterBeanDiscovery event,
            Class<?> rawType,
            java.lang.reflect.Type targetType,
            Set<Annotation> qualifiers,
            Supplier<Object> mockSupplier) {
        Class<? extends Annotation> scope = isJdkType(rawType) ? Dependent.class : RequestScoped.class;
        Set<Annotation> finalQualifiers = qualifiersWithDefaults(qualifiers);
        Set<java.lang.reflect.Type> types = beanTypes(targetType);
        event.addBean()
                .beanClass(rawType)
                .scope(scope)
                .types(types)
                .qualifiers(finalQualifiers)
                .produceWith(inst -> mockSupplier.get());
    }

    private static Set<Annotation> qualifiersWithDefaults(Set<Annotation> qualifiers) {
        Set<Annotation> result = new HashSet<>(qualifiers);
        boolean hasNamed = result.stream().anyMatch(q -> q.annotationType().getSimpleName().equals("Named"));
        boolean hasCustom = result.stream()
                .anyMatch(q -> !q.annotationType().equals(Default.class)
                        && !q.annotationType().equals(Any.class)
                        && !q.annotationType().getName().equals("jakarta.inject.Named"));
        if (!hasCustom && !hasNamed) {
            result.add(Default.Literal.INSTANCE);
        }
        result.add(Any.Literal.INSTANCE);
        return Collections.unmodifiableSet(result);
    }

    private static Set<java.lang.reflect.Type> beanTypes(java.lang.reflect.Type targetType) {
        Set<java.lang.reflect.Type> types = new HashSet<>();
        types.add(targetType);
        types.add(Object.class);
        return Collections.unmodifiableSet(types);
    }

    private static boolean isJdkType(Class<?> rawType) {
        String packageName = rawType.getPackageName();
        return packageName.startsWith("java.") || packageName.startsWith("javax.");
    }

    /**
     * Build a {@link NamedLiteral} for the given name, used by
     * callers that need to construct a synthetic {@code @Named}
     * qualifier programmatically.
     *
     * @param name the name to embed in the qualifier
     * @return a runtime {@code @Named} annotation literal
     */
    public static Annotation named(String name) {
        return NamedLiteral.of(name);
    }
}
