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
package org.os890.jawelte.module.dbtestdata.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.os890.jawelte.module.dbtestdata.api.port.ELInterpolator;

/**
 * Typed bundle of bindings the active {@link ELInterpolator} resolves
 * {@code ${expr}} occurrences against. Carries the three populator
 * shapes the diff-builder exposes:
 *
 * <ul>
 *   <li>{@code values} — flat name -&gt; object bindings (via
 *       {@code withValues(...)});</li>
 *   <li>{@code beans} — named object instances usable in method
 *       calls (via {@code withBean(...)});</li>
 *   <li>{@code functions} — registrations of static methods callable
 *       as {@code ${prefix:name(...)}} (via {@code withFunction(...)}).</li>
 * </ul>
 *
 * @param values    flat-substitution bindings
 * @param beans     named object instances
 * @param functions registered Jakarta EL function descriptors
 */
public record InterpolationContext(
        Map<String, Object> values,
        Map<String, Object> beans,
        List<ELFunctionDescriptor> functions) {

    /**
     * Canonical constructor. Defensively copies maps and lists so the
     * record stays immutable even if the caller mutates its source
     * collections after construction.
     */
    public InterpolationContext {
        values = Map.copyOf(Objects.requireNonNull(values, "values"));
        beans = Map.copyOf(Objects.requireNonNull(beans, "beans"));
        functions = List.copyOf(Objects.requireNonNull(functions, "functions"));
    }

    /** Convenience constant for the empty context (no bindings). */
    public static final InterpolationContext EMPTY = new InterpolationContext(Map.of(), Map.of(), List.of());
}
