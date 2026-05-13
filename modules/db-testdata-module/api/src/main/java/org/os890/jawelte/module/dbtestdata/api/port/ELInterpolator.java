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

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pluggable EL substitution and per-cell predicate evaluation. Three
 * methods cover the two phases the api distinguishes:
 *
 * <ul>
 *   <li><strong>pre-parse, immediate</strong> — text-level substitution
 *       of {@code ${expr}} ({@link #interpolate(String, Context)})
 *       or of both {@code ${expr}} and {@code #{expr}}
 *       ({@link #interpolateAll(String, Context)}). The diff-builder
 *       calls the {@code ${expr}}-only variant so {@code #{expr}}
 *       occurrences in expected datasets pass through to per-cell
 *       evaluation; the seed-builder calls the dual variant so
 *       {@code #{expr}} on the seed side behaves identically to
 *       {@code ${expr}} (no actual DB value exists at seed time, so
 *       deferred semantics make no sense there).</li>
 *   <li><strong>per-cell, deferred</strong> — boolean predicate
 *       evaluation of {@code #{expr}} against an actual database cell
 *       value ({@link #evaluatePredicate(String, Context, Object)}).
 *       The diff engine calls this whenever it encounters a
 *       {@code #{expr}} marker in an expected cell; the actual DB
 *       value is bound as {@code value} (and as {@code num} when it
 *       parses as a number) on top of the caller-supplied
 *       {@link Context}.</li>
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
     * {@link #evaluatePredicate(String, Context, Object)}.
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
    String interpolate(String template, Context context);

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
     *                          {@link #interpolate(String, Context)})
     */
    String interpolateAll(String template, Context context);

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
     * {@link #interpolate(String, Context)} on missing variables
     * (no silent fallback).
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
    boolean evaluatePredicate(String expression, Context context, Object actualValue);

    /**
     * Typed bundle of bindings the active {@link ELInterpolator}
     * resolves {@code ${expr}} / {@code #{expr}} occurrences against.
     * Carries the three populator shapes the diff-builder exposes:
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
    record Context(
            Map<String, Object> values,
            Map<String, Object> beans,
            List<FunctionDescriptor> functions) {

        /**
         * Canonical constructor. Defensively copies maps and lists so
         * the record stays immutable even if the caller mutates its
         * source collections after construction.
         */
        public Context {
            values = Map.copyOf(Objects.requireNonNull(values, "values"));
            beans = Map.copyOf(Objects.requireNonNull(beans, "beans"));
            functions = List.copyOf(Objects.requireNonNull(functions, "functions"));
        }

        /** Convenience constant for the empty context (no bindings). */
        public static final Context EMPTY = new Context(Map.of(), Map.of(), List.of());

        /**
         * Typed registration for a Jakarta EL function the diff-builder
         * advertises through {@code DbDiff.Builder.withFunction(...)}.
         * The declaring class and method name are carried as data
         * rather than as a {@link java.lang.reflect.Method} so the api
         * jar stays reflection-free at compile time; validation of the
         * actual method existence and {@code public static} modifier
         * happens at registration time inside the builder.
         *
         * @param prefix         the function prefix, e.g. {@code "fn"}
         *                       for {@code ${fn:now()}}
         * @param name           the function name, e.g. {@code "now"}
         * @param declaringClass the class that hosts the static method
         * @param methodName     the static-method name inside
         *                       {@code declaringClass}
         */
        public record FunctionDescriptor(
                String prefix,
                String name,
                Class<?> declaringClass,
                String methodName) {

            /** Canonical constructor; every field is mandatory. */
            public FunctionDescriptor {
                Objects.requireNonNull(prefix, "prefix");
                Objects.requireNonNull(name, "name");
                Objects.requireNonNull(declaringClass, "declaringClass");
                Objects.requireNonNull(methodName, "methodName");
            }
        }
    }
}
