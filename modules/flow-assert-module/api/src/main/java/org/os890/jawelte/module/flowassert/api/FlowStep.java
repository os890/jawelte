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
package org.os890.jawelte.module.flowassert.api;

/**
 * One line of a sequence-diagram, in a notation-independent shape —
 * the canonical model a
 * {@link org.os890.jawelte.module.flowassert.api.port.FlowDialect}
 * parses a diagram into and the
 * {@link org.os890.jawelte.module.flowassert.api.port.FlowDiffEngine}
 * compares.
 *
 * <p>The split between {@link #label()} and {@link #annotation()} is
 * what makes "ignore the timings" work for a custom dialect too:
 * everything that is stable goes into the label, everything that
 * changes from run to run (a duration, a timestamp, a thread name)
 * goes into the annotation, and the engine only looks at the
 * annotation when the assertion asked it to.
 *
 * @param kind       what this line is
 * @param from       the sending participant, or {@code null} when the
 *                   kind has no sender (a participant declaration, a
 *                   block boundary)
 * @param to         the receiving participant, or {@code null} when
 *                   the kind has no receiver
 * @param label      the stable part of the line: a call signature, a
 *                   returned type, a participant name, a loop count;
 *                   never {@code null}, possibly empty
 * @param annotation the volatile part of the line: a duration, a
 *                   timestamp, a thread name; never {@code null},
 *                   possibly empty
 * @param chainIndex 0-based index of the block this line belongs to in
 *                   a combined diagram, or {@code 0} for a diagram
 *                   holding a single chain
 * @param depth      call-nesting depth, counted from the outermost
 *                   call of the chain
 * @param lineNumber 1-based line number in the diagram this step was
 *                   parsed from
 */
public record FlowStep(
        Kind kind,
        String from,
        String to,
        String label,
        String annotation,
        int chainIndex,
        int depth,
        int lineNumber) {

    /**
     * Normalises {@code label} and {@code annotation} to the empty
     * string so neither the engine nor a message formatter has to
     * null-check them.
     *
     * @param kind       what this line is
     * @param from       the sending participant, or {@code null}
     * @param to         the receiving participant, or {@code null}
     * @param label      the stable part of the line
     * @param annotation the volatile part of the line
     * @param chainIndex 0-based block index
     * @param depth      call-nesting depth
     * @param lineNumber 1-based line number
     */
    public FlowStep {
        label = label == null ? "" : label;
        annotation = annotation == null ? "" : annotation;
    }

    /**
     * The kinds of line a sequence-diagram is made of. A dialect that
     * cannot express one of them simply never emits it.
     */
    public enum Kind {

        /** Notation boilerplate: {@code sequenceDiagram}, {@code @startuml}, {@code autonumber}. */
        HEADER,

        /** The diagram's title. */
        TITLE,

        /** A participant lane declaration. */
        PARTICIPANT,

        /** Start of one chain's block in a combined diagram. */
        CHAIN_START,

        /** The note naming a chain: label is the entry point, annotation its duration. */
        CHAIN_NOTE,

        /** End of one chain's block in a combined diagram. */
        CHAIN_END,

        /** The header note of a single-chain diagram: wall-clock and thread, volatile throughout. */
        HEADER_NOTE,

        /** A call from one participant to another. */
        CALL,

        /** A CDI event delivered to an observer method. */
        EVENT,

        /** A normal return; the label is the returned type. */
        RETURN,

        /** A return by exception; the label names the thrown type. */
        THROW,

        /** Start of a folded-loop block; the label is the iteration count. */
        LOOP_START,

        /** End of a folded-loop block. */
        LOOP_END,

        /** A hotspot marker. */
        HOTSPOT,

        /** Any other note. */
        NOTE
    }
}
