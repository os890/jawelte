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
package org.os890.jawelte.module.contentdiff.api.port;

import java.util.Map;

/**
 * Pluggable {@code ${expr}} substitution for the expected document.
 * The two built-in {@link DiffEngine} implementations call into the
 * active interpolator before parsing, so the diff sees the expanded
 * payload rather than the raw template.
 *
 * <p>The default implementation shipped in
 * {@code content-diff-module-impl} routes through Jakarta EL using
 * the spec's {@code ExpressionFactory.newInstance()} lookup — the
 * concrete EL runtime is whichever provider sits on the classpath.
 * The project pins Apache Tomcat's {@code tomcat-embed-el} as the
 * default in {@code depMgmt}; GlassFish Expressly is also pinned
 * (test scope) and consumers can swap to it by replacing the dep,
 * with no code change required (the same default interpolator works
 * against either provider).
 *
 * <p>Consumers who want different interpolation semantics altogether
 * (e.g. flat {@code ${name}} substitution with no EL grammar, or a
 * non-EL templating engine) register their own implementation via
 * {@code META-INF/services/org.os890.jawelte.module.contentdiff.api.port.ELInterpolator}
 * at a lower {@code @Priority} value — the project-wide priority
 * resolver picks it over the default.
 *
 * <p>Implementations must be thread-safe — a single instance is
 * shared across every concurrent diff call.
 */
public interface ELInterpolator {

    /**
     * Replace every {@code ${expression}} occurrence in
     * {@code template} with the evaluated value, computed against
     * {@code values}. Literals around the expressions and the
     * empty case (no expressions present) are returned verbatim.
     *
     * @param template the expected document template
     * @param values   bindings the interpolator may resolve
     *                 (Jakarta-EL-style property access, method
     *                 calls, etc. are at the implementation's
     *                 discretion)
     * @return the interpolated string
     * @throws RuntimeException at the implementation's discretion on
     *         evaluation failure — the default Jakarta EL impl lets
     *         {@code jakarta.el.PropertyNotFoundException},
     *         {@code MethodNotFoundException}, and {@code ELException}
     *         propagate
     */
    String interpolate(String template, Map<String, Object> values);
}
