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
package org.os890.jawelte.core.api.port;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;

/**
 * Driven port that decides, for a given bean class, whether and
 * how the CDI runtime should remap the bean's scope. The fixed
 * thin CDI Extension in {@code core/impl} delegates to whichever
 * implementation {@code TestContext.loadService(Class)} picks via
 * priority resolution; customers swap the active impl by
 * SL-registering their own at a higher priority.
 *
 * <p><b>Default impl.</b> {@code core/impl} ships a SL-registered
 * default that walks every {@link BeanScopeMapper} provider on the
 * classpath. The first provider whose {@link BeanScopeMapper#trigger()}
 * is directly present on the bean class drives the remap (subject
 * to {@link BeanScopeMapper#preserveExplicitDirectScopes()}). The
 * resulting {@link ScopeMappingMetadata} carries the target scope plus the set
 * of CDI scope annotations to strip from the type before adding
 * the new one.
 *
 * <p><b>CDI-typed surface deliberately avoided.</b> The port's
 * methods take plain reflection types ({@link Class},
 * {@link Field}, {@link Method}) — never a
 * {@code ProcessAnnotatedType} or any other CDI extension
 * lifecycle type. The fixed thin CDI extension translates the
 * port's class-level decision into
 * {@code AnnotatedTypeConfigurator} operations internally; the
 * Field / Method overloads are queried by cdi-module /
 * ejb-module from outside the CDI extension lifecycle (when
 * synthesising beans from {@code @TestBean} static fields or
 * producer methods). Custom port impls only need to answer
 * "given this declaration, what scope (if any) should it
 * have?".
 *
 * <p><b>Three overloads, one shared provider set.</b> All three
 * <code>mapScope</code> methods iterate the same SL-registered
 * {@link BeanScopeMapper} providers. The class-level overload
 * returns full {@link ScopeMappingMetadata} (the CDI extension
 * needs the set of annotations to strip from the type). The
 * Field / Method overloads return only the target scope —
 * cdi-module / ejb-module use it as the synthetic bean's
 * declared scope; there is no existing annotation to strip.
 */
public interface BeanScopeMapperPort {

    /**
     * Compute the scope remap to apply to the given bean class, if
     * any.
     *
     * @param beanClass the type CDI's {@code ProcessAnnotatedType}
     *                  event has delivered; never {@code null}
     * @return the {@link ScopeMappingMetadata} the CDI extension should apply,
     *         or {@link Optional#empty()} when no remap applies to
     *         this class
     */
    Optional<ScopeMappingMetadata> mapScope(Class<?> beanClass);

    /**
     * Compute the scope a synthetic bean derived from the given
     * field should carry. Called by cdi-module when it synthesises
     * a bean for an {@code @TestBean}-annotated static field. The
     * port walks the {@link BeanScopeMapper} providers and returns
     * the target of the first one whose {@link BeanScopeMapper#trigger()}
     * is directly present on the field.
     *
     * <p>Explicit-scope handling is the <b>caller's</b>
     * responsibility — cdi-module checks the field's own scope
     * annotations first, falls through to this method only when
     * none is present, and falls through to {@code @Dependent}
     * only when this method also returns {@link Optional#empty()}.
     *
     * @param testBeanField the static field declaring the synthetic
     *                      bean; never {@code null}
     * @return the target CDI scope, or {@link Optional#empty()} if
     *         no provider matched
     */
    Optional<Class<? extends Annotation>> mapScope(Field testBeanField);

    /**
     * Compute the scope a synthetic bean derived from the given
     * producer method should carry. Same contract as
     * {@link #mapScope(Field)} for the producer-method shape of
     * {@code @TestBean}.
     *
     * @param testBeanMethod the producer method declaring the
     *                       synthetic bean; never {@code null}
     * @return the target CDI scope, or {@link Optional#empty()} if
     *         no provider matched
     */
    Optional<Class<? extends Annotation>> mapScope(Method testBeanMethod);

    /**
     * Result of a successful {@link #mapScope(Class)} call.
     * Captures the full transformation the active port impl wants
     * the CDI extension to apply to the bean's
     * {@code AnnotatedType}:
     *
     * <ul>
     *   <li>{@link #targetScope()} — the CDI scope annotation
     *       class to add directly to the type. Must itself be a
     *       CDI scope (meta-annotated
     *       {@code jakarta.enterprise.context.NormalScope} or
     *       {@code jakarta.inject.Scope}).</li>
     *   <li>{@link #annotationsToRemove()} — the set of existing
     *       annotation types to strip from the type before adding
     *       the new scope, so the remap doesn't end up with two
     *       scopes (a CDI deployment error). This is the bean
     *       class's CDI scopes per CDI's effective-scope view (so it
     *       may include a scope inherited from a base via
     *       {@link java.lang.annotation.Inherited}); the
     *       {@code AnnotatedTypeConfigurator.remove(...)} step only
     *       affects directly-declared annotations, so an inherited
     *       entry is a harmless no-op. May be empty when the trigger
     *       is a stereotype contributing the scope indirectly (no
     *       direct scope to remove; the directly-added target wins
     *       via CDI's class-level-scope-wins rule).</li>
     * </ul>
     *
     * <p>Records are intentional: the mapping is immutable and
     * purely descriptive. The CDI extension (the fixed thin
     * wrapper in {@code core/impl}) reads both fields back and
     * applies them via
     * {@code AnnotatedTypeConfigurator.remove(...).add(...)}.
     *
     * @param targetScope         the CDI scope annotation to
     *                            install on the bean's type; never
     *                            {@code null}
     * @param annotationsToRemove the existing annotation types to
     *                            strip from the type; never
     *                            {@code null} (use {@link Set#of()}
     *                            for "add only"); defensively
     *                            copied to immutable form on
     *                            construction
     */
    record ScopeMappingMetadata(
            Class<? extends Annotation> targetScope,
            Set<Class<? extends Annotation>> annotationsToRemove) {

        /** Defensive-copy compact constructor. */
        public ScopeMappingMetadata {
            annotationsToRemove = Set.copyOf(annotationsToRemove);
        }
    }
}
