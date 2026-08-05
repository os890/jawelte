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
package org.os890.jawelte.tests.flowassert.scenario08;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.annotation.Priority;

import org.os890.cdi.uml.dynamic.flow.renderer.api.CallFlow;
import org.os890.cdi.uml.dynamic.flow.renderer.api.CallNode;
import org.os890.jawelte.module.flowassert.api.FlowStep;
import org.os890.jawelte.module.flowassert.api.port.FlowDialect;

/**
 * A third notation, contributed by the test rather than by the module:
 * a flat text format claiming {@code .flow}.
 *
 * <p>It implements nothing but rendering and parsing. The comparison,
 * the ignore lists, the timing handling and the failure message all
 * come from the module - which is the point of keeping
 * {@link FlowDialect} and
 * {@link org.os890.jawelte.module.flowassert.api.port.FlowDiffEngine}
 * separate ports.
 *
 * <p>Registered at {@code @Priority(500)}, so it outranks the two
 * built-ins and its extension is probed first by the convention.
 */
@Priority(500)
public class TestScenarioTextFlowDialect implements FlowDialect {

    private static final Set<String> EXTENSIONS =
            Collections.unmodifiableSet(new LinkedHashSet<>(List.of(".flow")));

    /** No-arg constructor required by SPI {@code ServiceLoader} lookup. */
    public TestScenarioTextFlowDialect() {
    }

    @Override
    public String name() {
        return "text";
    }

    @Override
    public Set<String> fileExtensions() {
        return EXTENSIONS;
    }

    @Override
    public String render(List<CallFlow> flows) {
        StringBuilder text = new StringBuilder();
        for (CallFlow flow : flows) {
            text.append("chain ").append(flow.entryTypeSimpleName()).append('.')
                    .append(flow.entryMethodName()).append('\n');
            appendNode(text, "Caller", flow.root());
            text.append("end\n");
        }
        return text.toString();
    }

    @Override
    public String renderSingle(CallFlow flow) {
        return render(List.of(flow));
    }

    @Override
    public List<FlowStep> parse(String diagram) {
        List<FlowStep> steps = new ArrayList<>();
        int chainIndex = -1;
        int depth = 0;
        int lineNumber = 0;
        for (String rawLine : diagram.split("\\R", -1)) {
            lineNumber++;
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("chain ")) {
                chainIndex++;
                depth = 0;
                steps.add(step(FlowStep.Kind.CHAIN_START, null, null, "", chainIndex, 0, lineNumber));
                steps.add(step(FlowStep.Kind.CHAIN_NOTE, null, null, line.substring(6).strip(),
                        chainIndex, 0, lineNumber));
                continue;
            }
            if ("end".equals(line)) {
                steps.add(step(FlowStep.Kind.CHAIN_END, null, null, "", chainIndex, 0, lineNumber));
                continue;
            }
            String[] parts = line.split(" ", 3);
            if (parts.length < 3) {
                throw new IllegalArgumentException("Cannot read line " + lineNumber + " as text: " + line);
            }
            String[] participants = parts[1].split(">", 2);
            FlowStep.Kind kind = kindOf(parts[0], lineNumber, line);
            if (kind == FlowStep.Kind.RETURN || kind == FlowStep.Kind.THROW) {
                depth = Math.max(0, depth - 1);
                steps.add(step(kind, participants[0], participants[1], parts[2],
                        chainIndex, depth, lineNumber));
                continue;
            }
            steps.add(step(kind, participants[0], participants[1], parts[2],
                    chainIndex, depth, lineNumber));
            depth++;
        }
        return List.copyOf(steps);
    }

    private static FlowStep.Kind kindOf(String token, int lineNumber, String line) {
        switch (token) {
            case "call":
                return FlowStep.Kind.CALL;
            case "event":
                return FlowStep.Kind.EVENT;
            case "return":
                return FlowStep.Kind.RETURN;
            case "throw":
                return FlowStep.Kind.THROW;
            default:
                throw new IllegalArgumentException(
                        "Cannot read line " + lineNumber + " as text: " + line);
        }
    }

    private static FlowStep step(FlowStep.Kind kind, String from, String to, String label,
                                 int chainIndex, int depth, int lineNumber) {
        return new FlowStep(kind, from, to, label, "", Math.max(chainIndex, 0), depth, lineNumber);
    }

    private static void appendNode(StringBuilder text, String callerId, CallNode node) {
        String id = node.beanSimpleName();
        text.append(node.isObserverMethod() ? "event " : "call ")
                .append(callerId).append('>').append(id).append(' ')
                .append(node.signature()).append('\n');
        for (CallNode child : node.children()) {
            appendNode(text, id, child);
        }
        text.append(node.hasFailed() ? "throw " : "return ")
                .append(id).append('>').append(callerId).append(' ')
                .append(node.hasFailed() ? node.thrownTypeName() : node.returnTypeName())
                .append('\n');
    }
}
