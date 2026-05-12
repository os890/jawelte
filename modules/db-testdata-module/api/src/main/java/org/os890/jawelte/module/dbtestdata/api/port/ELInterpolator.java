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
package org.os890.jawelte.module.dbtestdata.api.port;

import org.os890.jawelte.module.dbtestdata.api.InterpolationContext;

/**
 * Pluggable {@code ${expr}} substitution for dataset text. The diff-
 * and seed-builders call into the active interpolator before handing
 * the content to the engine, so the engine only ever parses the
 * post-interpolation string.
 *
 * <p>The default implementation in db-testdata-module/impl is
 * {@code JakartaELInterpolator}, which routes through Jakarta EL via
 * the spec's {@code ExpressionFactory.newInstance()} lookup so any
 * compliant provider (Tomcat's {@code tomcat-embed-el} or GlassFish
 * Expressly) wires in without a code change.
 *
 * <p>Consumers who want different semantics (e.g. flat
 * {@code ${name}} substitution that leaves unknown variables as the
 * literal token) ship their own impl at a lower {@code @Priority}
 * value — selected by the project-wide
 * {@code ServicePriorityResolver}.
 *
 * <p>Implementations must be thread-safe — a single instance is
 * cached per JVM and shared across every concurrent seed / diff call.
 */
public interface ELInterpolator {

    /**
     * Replace every {@code ${expr}} occurrence in {@code template}
     * with the evaluated value, computed against {@code context}.
     * Literals around the expressions and the empty case
     * (no {@code ${} present}) are returned verbatim.
     *
     * @param template the dataset text — possibly carrying
     *                 {@code ${expr}} occurrences
     * @param context  bindings (values, beans, function descriptors)
     *                 the interpolator may resolve
     * @return the interpolated string
     * @throws RuntimeException at the implementation's discretion on
     *                          evaluation failure — the default
     *                          Jakarta EL impl propagates
     *                          {@code jakarta.el.ELException}
     *                          subtypes
     */
    String interpolate(String template, InterpolationContext context);
}
