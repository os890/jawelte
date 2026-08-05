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
package org.os890.jawelte.module.flowassert.api.port;

import java.util.List;
import java.util.Set;

import org.os890.cdi.uml.dynamic.flow.renderer.api.CallFlow;
import org.os890.jawelte.module.flowassert.api.FlowStep;

/**
 * One notation: how a recording is written down, and how a written
 * diagram is read back. The port to implement for a custom diagram
 * format.
 *
 * <p>Implementations register via
 * {@code META-INF/services/org.os890.jawelte.module.flowassert.api.port.FlowDialect}
 * and carry a {@code jakarta.annotation.Priority} for ordering
 * (lowest value wins; full class names break ties).
 *
 * <p><strong>Selection</strong> happens by file extension:
 * {@code FlowDiff} enumerates every registered dialect via
 * {@link java.util.ServiceLoader}, keeps those whose
 * {@link #fileExtensions()} contain the extension of the expected
 * resource, and hands the rest to the active
 * {@link org.os890.jawelte.core.api.port.ServicePriorityResolver}.
 * The winner is cached per extension for the JVM lifetime. Two
 * dialects claiming the same extension therefore compete by priority
 * rather than one silently shadowing the other.
 *
 * <p>Implementations must be thread-safe — one instance is shared
 * across every assertion.
 *
 * <p>The two built-in dialects render by delegating to the recorder
 * ({@code CombinedFlowDiagram.of(...)}, {@code CallFlow#toMermaid()},
 * {@code CallFlow#toPlantUml()}), so the notation of a recording is
 * decided per assertion and {@code cdi-flow.output-format} never
 * leaks into a comparison.
 */
public interface FlowDialect {

    /**
     * The name of the notation, as used by
     * {@code FlowDiff.Builder#expectedContent(String, String)} and in
     * failure messages. Case-insensitive on lookup.
     *
     * <p>Stable for the lifetime of the instance.
     *
     * @return the notation name, e.g. {@code "mermaid"}; never {@code null}
     */
    String name();

    /**
     * The file extensions this dialect claims, leading dot included
     * and lower-case, e.g. {@code {".mmd", ".mermaid"}}.
     *
     * <p>Stable for the lifetime of the instance.
     *
     * @return the claimed extensions; never {@code null}, never empty
     */
    Set<String> fileExtensions();

    /**
     * Render several recorded flows as <strong>one</strong> diagram —
     * one block per flow, in the given order, sharing the participant
     * lanes. This is what an assertion compares by default.
     *
     * @param flows the flows to render, in recording order; never
     *              {@code null}, possibly empty
     * @return the diagram; never {@code null}
     */
    String render(List<CallFlow> flows);

    /**
     * Render a single recorded flow as a diagram of its own, without
     * the combined-diagram wrapper.
     *
     * @param flow the flow to render; never {@code null}
     * @return the diagram; never {@code null}
     */
    String renderSingle(CallFlow flow);

    /**
     * Parse a diagram into the canonical model. Called for the
     * expected side as well as for the rendered actual side, so both
     * are compared through the same reading of the notation.
     *
     * <p>Every returned step carries the 1-based line number it was
     * parsed from — that number is what a failure message points the
     * reader at.
     *
     * @param diagram the diagram text; never {@code null}
     * @return the steps, in document order; never {@code null}
     * @throws IllegalArgumentException if the text cannot be read as
     *         this notation
     */
    List<FlowStep> parse(String diagram);
}
