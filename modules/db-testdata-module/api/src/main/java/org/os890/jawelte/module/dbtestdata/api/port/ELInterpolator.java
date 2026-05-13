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
 * Pluggable EL substitution and per-cell predicate evaluation. Three
 * methods cover the two phases the api distinguishes:
 *
 * <ul>
 *   <li><strong>pre-parse, immediate</strong> — text-level substitution
 *       of {@code ${expr}} ({@link #interpolate(String, InterpolationContext)})
 *       or of both {@code ${expr}} and {@code #{expr}}
 *       ({@link #interpolateAll(String, InterpolationContext)}). The
 *       diff-builder calls the {@code ${expr}}-only variant so
 *       {@code #{expr}} occurrences in expected datasets pass through
 *       to per-cell evaluation; the seed-builder calls the dual variant
 *       so {@code #{expr}} on the seed side behaves identically to
 *       {@code ${expr}} (no actual DB value exists at seed time, so
 *       deferred semantics make no sense there).</li>
 *   <li><strong>per-cell, deferred</strong> — boolean predicate
 *       evaluation of {@code #{expr}} against an actual database cell
 *       value ({@link #evaluatePredicate(String, InterpolationContext, Object)}).
 *       The diff engine calls this whenever it encounters a
 *       {@code #{expr}} marker in an expected cell; the actual DB
 *       value is bound as {@code value} (and as {@code num} when it
 *       parses as a number) on top of the caller-supplied
 *       {@link InterpolationContext}.</li>
 * </ul>
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
     * {@code #{expr}} occurrences are left untouched so the diff
     * engine can dispatch them to
     * {@link #evaluatePredicate(String, InterpolationContext, Object)}.
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

    /**
     * Replace every {@code ${expr}} <em>and</em> {@code #{expr}}
     * occurrence in {@code template} with the evaluated value. Used
     * by the seed-builder so {@code #{expr}} on the seed side acts as
     * an immediate placeholder (same semantics as {@code ${expr}});
     * the seed engine has no per-cell evaluation step, so a
     * {@code #{expr}} that survived to seed time would be written to
     * the database verbatim — which is never what the test author
     * meant.
     *
     * @param template the dataset text
     * @param context  bindings (values, beans, function descriptors)
     * @return the interpolated string with both {@code ${&hellip;}} and
     *         {@code #{&hellip;}} resolved
     * @throws RuntimeException on evaluation failure (same propagation
     *                          rules as
     *                          {@link #interpolate(String, InterpolationContext)})
     */
    String interpolateAll(String template, InterpolationContext context);

    /**
     * Evaluate {@code expression} (in the form {@code #{&hellip;}}) as a
     * boolean predicate against {@code actualValue}. The actual DB
     * value is bound on top of {@code context} as the variable
     * {@code value}; if it parses as a {@link Double} via
     * {@link Double#parseDouble(String)} on its string form, it is
     * <em>also</em> bound as {@code num}. Bindings already present in
     * {@code context} for the names {@code value} / {@code num} are
     * overridden by the cell bindings.
     *
     * <p>The result must be a non-{@code null} {@link Boolean};
     * anything else raises {@link RuntimeException} naming the
     * offending expression. This matches the strict-EL stance of
     * {@link #interpolate(String, InterpolationContext)} on missing
     * variables (no silent fallback).
     *
     * @param expression  the full {@code #{&hellip;}} expression as
     *                    written in the expected dataset
     * @param context     caller-supplied bindings (values, beans,
     *                    function descriptors)
     * @param actualValue the value the database returned for the cell;
     *                    {@code null} for SQL NULL (the diff engine
     *                    short-circuits before this method is called
     *                    on NULL cells, so impls may assume non-null
     *                    in practice — callers should still pass the
     *                    raw value)
     * @return the predicate's boolean result
     * @throws RuntimeException on evaluation failure or non-boolean
     *                          result
     */
    boolean evaluatePredicate(String expression, InterpolationContext context, Object actualValue);
}
