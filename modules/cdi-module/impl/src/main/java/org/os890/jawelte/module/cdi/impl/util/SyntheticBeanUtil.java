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
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.inject.Named;

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
     * Register a synthetic bean whose instance is the value held by a
     * {@code @TestBean} static field. The caller resolves the bean's
     * scope per the precedence rules and passes it in.
     *
     * <p>Precedence (resolved by
     * {@link org.os890.jawelte.module.cdi.impl.adapter.extension.TestBeansCdiExtension}):
     * a CDI scope annotation declared by the test author on the
     * static field; the
     * {@link org.os890.jawelte.core.api.port.ScopeBinding.TestBeanDefaultScope}
     * record bound on {@code TestContext} (when scope-module is on
     * the classpath); cdi-module's {@code @Singleton} fallback.
     *
     * @param event           the {@code AfterBeanDiscovery} event
     * @param fieldType       the declared type of the field
     * @param fieldValue      the field value (the bean instance)
     * @param qualifiers      qualifier annotations from the field plus
     *                        defaults; passed through verbatim
     * @param scope           the resolved CDI scope to register the
     *                        synthetic bean with
     */
    public static void registerStaticFieldBean(
            AfterBeanDiscovery event,
            Class<?> fieldType,
            Object fieldValue,
            Set<Annotation> qualifiers,
            Class<? extends Annotation> scope) {
        Set<Annotation> finalQualifiers = qualifiersWithDefaults(qualifiers);
        Set<Type> types = beanTypes(fieldType);
        event.addBean()
                .beanClass(fieldType)
                .scope(scope)
                .types(types)
                .qualifiers(finalQualifiers)
                .produceWith(inst -> fieldValue);
    }

    /**
     * Register an auto-mock synthetic bean for an unsatisfied
     * injection-point type. JDK types (everything in {@code java.} /
     * {@code javax.} packages) get {@link Dependent} scope
     * unconditionally because the CDI normal-scope proxy does not
     * work on {@code final} JDK classes; everything else uses the
     * caller-supplied {@code nonJdkScope}.
     *
     * <p>{@code nonJdkScope} is resolved by
     * {@link org.os890.jawelte.module.cdi.impl.adapter.extension.TestBeansCdiExtension}:
     * the {@link org.os890.jawelte.core.api.port.ScopeBinding.AutoMockDefaultScope}
     * record bound on {@code TestContext} (when scope-module is on
     * the classpath); cdi-module's {@code @RequestScoped} fallback.
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
     * @param nonJdkScope the resolved scope to use when {@code rawType}
     *                    is not a JDK type
     */
    public static void registerAutoMockBean(
            AfterBeanDiscovery event,
            Class<?> rawType,
            Type targetType,
            Set<Annotation> qualifiers,
            Supplier<Object> mockSupplier,
            Class<? extends Annotation> nonJdkScope) {
        Class<? extends Annotation> scope = isJdkType(rawType) ? Dependent.class : nonJdkScope;
        Set<Annotation> finalQualifiers = qualifiersWithDefaults(qualifiers);
        Set<Type> types = beanTypes(targetType);
        event.addBean()
                .beanClass(rawType)
                .scope(scope)
                .types(types)
                .qualifiers(finalQualifiers)
                .produceWith(inst -> mockSupplier.get());
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

    private static Set<Annotation> qualifiersWithDefaults(Set<Annotation> qualifiers) {
        Set<Annotation> result = new HashSet<>(qualifiers);
        boolean hasNamed = result.stream().anyMatch(q -> q.annotationType().equals(Named.class));
        boolean hasCustom = result.stream()
                .anyMatch(q -> !q.annotationType().equals(Default.class)
                        && !q.annotationType().equals(Any.class)
                        && !q.annotationType().equals(Named.class));
        if (!hasCustom && !hasNamed) {
            result.add(Default.Literal.INSTANCE);
        }
        result.add(Any.Literal.INSTANCE);
        return Collections.unmodifiableSet(result);
    }

    private static Set<Type> beanTypes(Type targetType) {
        Set<Type> types = new HashSet<>();
        types.add(targetType);
        types.add(Object.class);
        return Collections.unmodifiableSet(types);
    }

    private static boolean isJdkType(Class<?> rawType) {
        String packageName = rawType.getPackageName();
        return packageName.startsWith("java.") || packageName.startsWith("javax.");
    }
}
