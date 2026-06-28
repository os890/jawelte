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

/**
 * SPI for declarative CDI scope rewriting at
 * {@code ProcessAnnotatedType} time. Providers are loaded via
 * {@link java.util.ServiceLoader} by the active
 * {@link BeanScopeMapperPort} implementation and consumed in a
 * single pass over the bean archive — so adding a new remap is
 * just shipping one {@code BeanScopeMapper} implementation plus a
 * {@code META-INF/services/} line; no new CDI Extension class
 * per remap.
 *
 * <p>Providers are ordered by {@link ServicePriorityResolver}
 * (lowest {@code jakarta.annotation.Priority} first; providers
 * without {@code @Priority} sort last; ties broken by class name)
 * — the same selection rule as every other multi-impl SPI. A
 * consumer therefore overrides a built-in remap for a given
 * trigger by shipping a higher-precedence (lower-numeric
 * {@code @Priority}) provider for that trigger.
 *
 * <p>For every type the CDI runtime delivers as a
 * {@code ProcessAnnotatedType} event, the port walks the
 * registered providers in that priority order and, on the first
 * whose {@link #trigger()} annotation is directly present on the
 * type, applies the remap:
 *
 * <ol>
 *   <li>Optionally short-circuits when
 *       {@link #preserveExplicitDirectScopes()} is {@code true}
 *       AND the type carries a CDI scope that is neither the
 *       trigger itself nor the trigger's stereotype-contributed
 *       scope (i.e. the user declared a different scope and wants
 *       it preserved). "Carries" follows CDI's effective-scope
 *       view, so a scope inherited from a base class via
 *       {@link java.lang.annotation.Inherited} also short-circuits
 *       the remap — see {@link #preserveExplicitDirectScopes()}
 *       for the rationale and the subclass-override remedy.</li>
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
 *
 * <p><b>Hex-arch note.</b> {@code BeanScopeMapper} lives in
 * {@code core/api/port} so any feature module can ship a mapper
 * without compile-depending on scope-module's own jar. The
 * {@code targetScope()} return type is a plain
 * {@code Class<? extends Annotation>} — a mapper that targets a
 * scope-module-defined annotation (e.g.
 * {@code @TestClassScoped}) still needs scope-module/api at
 * compile time for the {@code .class} reference, but that's an
 * explicit dependency on a specific annotation, not an implicit
 * dependency on the SPI itself.
 */
public interface BeanScopeMapper {

    /**
     * The marker annotation whose direct presence on a bean's
     * class triggers this remap. Implementations return a single
     * annotation class — the trigger is matched via
     * {@link Class#isAnnotationPresent(Class)}.
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
     * <p>May return {@code null} to opt out of the remap at lookup
     * time. The active {@code BeanScopeMapperPort} (and any custom
     * impl) skips a mapper whose {@code targetScope()} resolves to
     * {@code null} and continues with the next mapper. This is the
     * documented escape hatch for providers that load their target
     * scope class reflectively — when the scope-defining module
     * isn't on the runtime classpath, the provider returns
     * {@code null} and the type's declared scope is left untouched.
     *
     * @return the replacement CDI scope annotation class, or
     *         {@code null} to skip this provider for this lookup
     */
    Class<? extends Annotation> targetScope();

    /**
     * Whether the remap should be skipped when the bean's class
     * already carries an explicit CDI scope other than the trigger
     * or the trigger's stereotype-contributed scope.
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
     * <p><strong>Inherited scopes count.</strong> "Carries a scope"
     * follows CDI's effective-scope view, i.e. it includes a scope
     * <em>inherited from a base class</em> via
     * {@link java.lang.annotation.Inherited} — and all built-in CDI
     * scopes ({@code @RequestScoped}, {@code @SessionScoped},
     * {@code @ApplicationScoped}, …) are {@code @Inherited}. So a
     * trigger-annotated subclass that extends a scoped base (e.g.
     * {@code @ConfigBean class Sub extends @RequestScoped Base}) is
     * treated as already having a defined scope and is left
     * untouched. That is deliberate and CDI-consistent: per CDI's
     * own rules {@code Sub} genuinely resolves to the base's scope,
     * and our rule is to not remap a bean whose scope is already
     * defined. If a subclass needs the remap target instead (e.g.
     * {@code @TestClassScoped}), the user declares that scope
     * <em>directly on the subclass</em>; a directly-declared scope
     * overrides the base's inherited one (CDI's
     * class-level-scope-wins rule), giving the subclass the intended
     * scope regardless of this short-circuit.
     *
     * @return {@code true} to skip the remap when an explicit
     *         non-default CDI scope is present on the type — declared
     *         directly <em>or</em> inherited from a base class;
     *         {@code false} (the default) to always apply the remap
     *         when the trigger is present
     */
    default boolean preserveExplicitDirectScopes() {
        return false;
    }
}
