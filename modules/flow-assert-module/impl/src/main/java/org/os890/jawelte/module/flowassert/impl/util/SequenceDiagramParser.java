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
package org.os890.jawelte.module.flowassert.impl.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.os890.jawelte.module.flowassert.api.FlowStep;

/**
 * Reads a rendered sequence-diagram back into {@link FlowStep}s — the
 * shared machinery behind the two built-in dialects.
 *
 * <p>Both notations are parsed by the same state machine: the chain
 * blocks of a combined diagram give every step its
 * {@link FlowStep#chainIndex()}, call and return arrows give it its
 * {@link FlowStep#depth()}, and everything volatile (a duration, a
 * timestamp, a thread name) is split off into
 * {@link FlowStep#annotation()} so the comparison can leave it out.
 *
 * <p>A line neither notation accounts for is an error rather than a
 * step nobody compares: an expected diagram with a typo has to fail
 * loudly, not silently match.
 *
 * <p>{@code abstract} plus a private constructor per the project's
 * static-utility class convention.
 */
public abstract class SequenceDiagramParser {

    private static final Pattern MERMAID_MESSAGE =
            Pattern.compile("^(\\w+)\\s*(-->>|->>|--x|-x|-\\))\\s*(\\w+)\\s*:\\s*(.*)$");

    private static final Pattern PLANTUML_MESSAGE =
            Pattern.compile("^(\\w+)\\s+(-->x|-->|->>|->)\\s+(\\w+)\\s+:\\s+(.*)$");

    private static final Pattern MERMAID_NOTE =
            Pattern.compile("^Note over ([\\w,\\s]+):\\s*(.*)$");

    private static final Pattern PLANTUML_INLINE_NOTE =
            Pattern.compile("^note over ([\\w,\\s]+)\\s+:\\s*(.*)$");

    private static final Pattern PLANTUML_BLOCK_NOTE =
            Pattern.compile("^note over ([\\w,\\s]+)$");

    private static final Pattern RECT_START =
            Pattern.compile("^rect\\s+rgb\\(\\s*\\d+\\s*,\\s*\\d+\\s*,\\s*\\d+\\s*\\)$");

    private static final Pattern LOOP_START = Pattern.compile("^loop\\s+(\\d+)\\s+times$");

    private static final Pattern TRAILING_ANNOTATION = Pattern.compile("^(.*?)\\s*\\[([^\\]]*)]$");

    /** {@code RecordedChain} separates the entry point from the timing with an em dash. */
    private static final String TITLE_SEPARATOR = " — ";

    private static final String HOTSPOT_MARKER = "HOTSPOT";

    private SequenceDiagramParser() {
    }

    /**
     * Parse a Mermaid sequence-diagram.
     *
     * @param diagram the diagram text; must not be {@code null}
     * @return the steps in document order; never {@code null}
     * @throws IllegalArgumentException on a line that is not Mermaid
     *         as this project's renderer writes it
     */
    public static List<FlowStep> parseMermaid(String diagram) {
        State state = new State();
        int lineNumber = 0;
        for (String rawLine : diagram.split("\\R", -1)) {
            lineNumber++;
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("%%")) {
                continue;
            }
            if ("---".equals(line)) {
                continue;
            }
            if (line.startsWith("title:")) {
                state.add(FlowStep.Kind.TITLE, null, null, unquote(line.substring(6).strip()), "", lineNumber);
                continue;
            }
            if ("sequenceDiagram".equals(line) || "autonumber".equals(line)) {
                state.add(FlowStep.Kind.HEADER, null, null, line, "", lineNumber);
                continue;
            }
            if (line.startsWith("participant ")) {
                state.add(FlowStep.Kind.PARTICIPANT, null, null, line.substring(12).strip(), "", lineNumber);
                continue;
            }
            if (RECT_START.matcher(line).matches()) {
                state.startChain(lineNumber);
                continue;
            }
            if ("end".equals(line)) {
                state.endBlock(lineNumber);
                continue;
            }
            Matcher loop = LOOP_START.matcher(line);
            if (loop.matches()) {
                state.startLoop(loop.group(1), lineNumber);
                continue;
            }
            if (line.startsWith("activate ") || line.startsWith("deactivate ")) {
                continue;
            }
            Matcher note = MERMAID_NOTE.matcher(line);
            if (note.matches()) {
                state.addNote(note.group(2).strip(), lineNumber);
                continue;
            }
            Matcher message = MERMAID_MESSAGE.matcher(line);
            if (message.matches()) {
                state.addMessage(mermaidKind(message.group(2), lineNumber, line),
                        message.group(1), message.group(3), message.group(4).strip(), lineNumber);
                continue;
            }
            throw unreadable("Mermaid", lineNumber, line);
        }
        return state.steps();
    }

    /**
     * Parse a PlantUML sequence-diagram.
     *
     * @param diagram the diagram text; must not be {@code null}
     * @return the steps in document order; never {@code null}
     * @throws IllegalArgumentException on a line that is not PlantUML
     *         as this project's renderer writes it
     */
    public static List<FlowStep> parsePlantUml(String diagram) {
        State state = new State();
        int lineNumber = 0;
        boolean insideNoteBlock = false;
        StringBuilder noteBlock = new StringBuilder();
        int noteBlockLine = 0;
        for (String rawLine : diagram.split("\\R", -1)) {
            lineNumber++;
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("'")) {
                continue;
            }
            if (insideNoteBlock) {
                if ("end note".equals(line)) {
                    insideNoteBlock = false;
                    state.add(FlowStep.Kind.HEADER_NOTE, null, null, "", noteBlock.toString().strip(),
                            noteBlockLine);
                    noteBlock.setLength(0);
                } else {
                    noteBlock.append(line).append(' ');
                }
                continue;
            }
            if ("@startuml".equals(line) || "@enduml".equals(line)
                    || "autonumber".equals(line) || "hide footbox".equals(line)) {
                state.add(FlowStep.Kind.HEADER, null, null, line, "", lineNumber);
                continue;
            }
            if (line.startsWith("title ")) {
                state.add(FlowStep.Kind.TITLE, null, null, line.substring(6).strip(), "", lineNumber);
                continue;
            }
            if (line.startsWith("participant ")) {
                state.add(FlowStep.Kind.PARTICIPANT, null, null, line.substring(12).strip(), "", lineNumber);
                continue;
            }
            if (line.startsWith("group ")) {
                state.startChain(lineNumber);
                state.addChainNote(line.substring(6).strip(), lineNumber);
                continue;
            }
            if ("end".equals(line)) {
                state.endBlock(lineNumber);
                continue;
            }
            Matcher loop = LOOP_START.matcher(line);
            if (loop.matches()) {
                state.startLoop(loop.group(1), lineNumber);
                continue;
            }
            if (line.startsWith("activate ") || line.startsWith("deactivate ")) {
                continue;
            }
            Matcher inlineNote = PLANTUML_INLINE_NOTE.matcher(line);
            if (inlineNote.matches()) {
                state.addNote(inlineNote.group(2).strip(), lineNumber);
                continue;
            }
            if (PLANTUML_BLOCK_NOTE.matcher(line).matches()) {
                insideNoteBlock = true;
                noteBlockLine = lineNumber;
                continue;
            }
            Matcher message = PLANTUML_MESSAGE.matcher(line);
            if (message.matches()) {
                state.addMessage(plantUmlKind(message.group(2), lineNumber, line),
                        message.group(1), message.group(3), message.group(4).strip(), lineNumber);
                continue;
            }
            throw unreadable("PlantUML", lineNumber, line);
        }
        return state.steps();
    }

    private static FlowStep.Kind mermaidKind(String arrow, int lineNumber, String line) {
        switch (arrow) {
            case "->>":
                return FlowStep.Kind.CALL;
            case "-->>":
                return FlowStep.Kind.RETURN;
            case "--x":
            case "-x":
                return FlowStep.Kind.THROW;
            case "-)":
                return FlowStep.Kind.EVENT;
            default:
                throw unreadable("Mermaid", lineNumber, line);
        }
    }

    private static FlowStep.Kind plantUmlKind(String arrow, int lineNumber, String line) {
        switch (arrow) {
            case "->":
                return FlowStep.Kind.CALL;
            case "-->":
                return FlowStep.Kind.RETURN;
            case "-->x":
                return FlowStep.Kind.THROW;
            case "->>":
                return FlowStep.Kind.EVENT;
            default:
                throw unreadable("PlantUML", lineNumber, line);
        }
    }

    private static IllegalArgumentException unreadable(String notation, int lineNumber, String line) {
        return new IllegalArgumentException(
                "Cannot read line " + lineNumber + " as " + notation + ": " + line);
    }

    private static String unquote(String value) {
        if (value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    /**
     * The state machine both notations drive: which chain block and
     * which call depth the next step belongs to.
     */
    private static class State {

        private final List<FlowStep> steps = new ArrayList<>();

        /** {@code true} for a chain block, {@code false} for a folded loop. */
        private final Deque<Boolean> openBlocks = new ArrayDeque<>();

        private int chainIndex = -1;
        private int depth;
        private boolean chainNoteExpected;

        private List<FlowStep> steps() {
            return List.copyOf(steps);
        }

        private void startChain(int lineNumber) {
            chainIndex++;
            depth = 0;
            openBlocks.push(Boolean.TRUE);
            chainNoteExpected = true;
            add(FlowStep.Kind.CHAIN_START, null, null, "", "", lineNumber);
        }

        private void startLoop(String count, int lineNumber) {
            openBlocks.push(Boolean.FALSE);
            add(FlowStep.Kind.LOOP_START, null, null, count, "", lineNumber);
        }

        private void endBlock(int lineNumber) {
            Boolean chainBlock = openBlocks.poll();
            if (Boolean.TRUE.equals(chainBlock)) {
                add(FlowStep.Kind.CHAIN_END, null, null, "", "", lineNumber);
            } else {
                add(FlowStep.Kind.LOOP_END, null, null, "", "", lineNumber);
            }
        }

        private void addNote(String text, int lineNumber) {
            if (text.startsWith(HOTSPOT_MARKER)) {
                add(FlowStep.Kind.HOTSPOT, null, null, text, "", lineNumber);
                return;
            }
            if (chainNoteExpected) {
                addChainNote(text, lineNumber);
                return;
            }
            // the prologue note of a single-chain diagram: wall-clock, duration and
            // thread-name, every one of them different on the next run
            add(FlowStep.Kind.HEADER_NOTE, null, null, "", text, lineNumber);
        }

        private void addChainNote(String text, int lineNumber) {
            chainNoteExpected = false;
            int separator = text.indexOf(TITLE_SEPARATOR);
            if (separator < 0) {
                add(FlowStep.Kind.CHAIN_NOTE, null, null, text, "", lineNumber);
                return;
            }
            add(FlowStep.Kind.CHAIN_NOTE, null, null, text.substring(0, separator).strip(),
                    text.substring(separator + TITLE_SEPARATOR.length()).strip(), lineNumber);
        }

        private void addMessage(FlowStep.Kind kind, String from, String to, String label, int lineNumber) {
            chainNoteExpected = false;
            Matcher annotated = TRAILING_ANNOTATION.matcher(label);
            String text = label;
            String annotation = "";
            if (annotated.matches()) {
                text = annotated.group(1).strip();
                annotation = annotated.group(2).strip();
            }
            if (kind == FlowStep.Kind.RETURN || kind == FlowStep.Kind.THROW) {
                depth = Math.max(0, depth - 1);
                add(kind, from, to, text, annotation, lineNumber);
                return;
            }
            add(kind, from, to, text, annotation, lineNumber);
            depth++;
        }

        private void add(FlowStep.Kind kind, String from, String to, String label,
                         String annotation, int lineNumber) {
            steps.add(new FlowStep(kind, from, to, label, annotation,
                    Math.max(chainIndex, 0), depth, lineNumber));
        }
    }
}
