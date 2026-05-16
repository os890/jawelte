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
 * <p><b>CDI-typed surface deliberately avoided.</b> This port's
 * single method takes a plain {@link Class}, not a
 * {@code ProcessAnnotatedType} — so the port contract has no
 * reference to CDI extension lifecycle types. The CDI extension
 * (which IS coupled to those types) translates the port's
 * decision into {@code AnnotatedTypeConfigurator} operations
 * internally. Custom port impls therefore don't need to know
 * anything about CDI extension internals; they just answer the
 * question "given this bean class, what scope (if any) should it
 * have, and what existing scope annotations should be removed
 * first?".
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
     *   <li>{@link #annotationsToRemove()} — the exact set of
     *       existing annotation types to strip from the type
     *       before adding the new scope. Typically the bean
     *       class's directly-declared CDI scopes (so the remap
     *       doesn't end up with two scopes — a CDI deployment
     *       error). May be empty when the trigger is a stereotype
     *       contributing the scope indirectly (no direct scope to
     *       remove; the directly-added target wins via CDI's
     *       class-level-scope-wins rule).</li>
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
