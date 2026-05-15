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
package org.os890.jawelte.module.scope.api;

import java.lang.annotation.Annotation;

/**
 * SPI for declarative CDI scope rewriting at
 * {@code ProcessAnnotatedType} time. Providers are loaded by
 * scope-module's {@code ScopeRemapCdiExtension} via
 * {@link java.util.ServiceLoader} and consumed in a single pass
 * over the bean archive — so adding a new remap is just shipping
 * one {@code AnnotationScopeRemap} implementation plus a
 * {@code META-INF/services/} line; no new CDI Extension class
 * per remap.
 *
 * <p>For every type the CDI runtime delivers as a
 * {@code ProcessAnnotatedType} event, the extension walks the
 * registered providers and, on the first whose {@link #trigger()}
 * annotation is directly present on the type, applies the remap:
 *
 * <ol>
 *   <li>Optionally short-circuits when
 *       {@link #preserveExplicitDirectScopes()} is {@code true}
 *       AND the type's direct annotations include a CDI scope
 *       that is neither the trigger itself nor the trigger's
 *       stereotype-contributed scope (i.e. the user explicitly
 *       declared a different scope and wants it preserved).</li>
 *   <li>Otherwise removes every direct CDI scope annotation
 *       from the type and adds the {@link #targetScope()} as a
 *       direct annotation. The {@code remove} step affects only
 *       directly-declared annotations; stereotype-contributed
 *       scopes are not in the iteration set, and the
 *       directly-added target wins over them per CDI's
 *       class-level-scope-wins rule.</li>
 * </ol>
 *
 * <p>Two scenarios behave uniformly through the same flow:
 *
 * <ul>
 *   <li><b>Direct scope</b> — e.g.
 *       {@code @SessionScoped class X}: the trigger IS a CDI
 *       scope and is directly present, so it is removed and the
 *       target replaces it.</li>
 *   <li><b>Stereotype with a contributed scope</b> — e.g.
 *       {@code @ConfigBean class Y} (where
 *       {@code @ConfigBean} is meta-annotated
 *       {@code @ApplicationScoped}): the trigger is a stereotype,
 *       not a scope. Nothing is removed (the contributed scope
 *       is not direct); the target is added directly and wins
 *       over the stereotype's contribution.</li>
 * </ul>
 */
public interface AnnotationScopeRemap {

    /**
     * The marker annotation whose direct presence on a bean's
     * {@code AnnotatedType} triggers this remap. Implementations
     * return a single annotation class — the trigger is matched
     * via {@code AnnotatedType.isAnnotationPresent(trigger())}.
     *
     * @return the trigger annotation class
     */
    Class<? extends Annotation> trigger();

    /**
     * The CDI scope annotation installed on the type in place of
     * whatever scope it currently carries. Must itself be a CDI
     * scope — i.e. meta-annotated with
     * {@code jakarta.enterprise.context.NormalScope} or
     * {@code jakarta.inject.Scope}.
     *
     * @return the replacement CDI scope annotation class
     */
    Class<? extends Annotation> targetScope();

    /**
     * Whether the remap should be skipped when the bean's
     * {@code AnnotatedType} directly carries an explicit CDI
     * scope other than the trigger or the trigger's
     * stereotype-contributed scope.
     *
     * <p>Useful for stereotype-style triggers (e.g.
     * {@code @ConfigBean}) where the user might add an explicit
     * non-default scope alongside the stereotype to opt out of
     * the default behaviour; the remap then leaves the user's
     * choice in place. Direct-scope triggers (e.g.
     * {@code @SessionScoped}) typically return {@code false} —
     * there is no separate user-override semantic to preserve
     * since the trigger IS the scope.
     *
     * @return {@code true} to skip the remap when an explicit
     *         non-default scope is directly declared on the type;
     *         {@code false} (the default) to always apply the
     *         remap when the trigger is present
     */
    default boolean preserveExplicitDirectScopes() {
        return false;
    }
}
