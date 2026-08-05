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

import java.util.ArrayList;
import java.util.List;

import org.os890.jawelte.module.flowassert.api.FlowStep;

/**
 * One block of a combined diagram: the outermost call it belongs to,
 * and the steps inside it.
 *
 * <p>A diagram holding a single chain has no block markers at all, so
 * it yields exactly one {@code DiagramChain} with an empty label —
 * which is what lets the comparison treat both shapes the same way.
 *
 * <p>Participant declarations and the title are left out: they belong
 * to the diagram, not to one of its chains, and are compared
 * separately.
 *
 * @param label      the entry point naming this chain
 *                   ({@code Type.method}), or {@code ""} for the
 *                   implicit chain of a single-chain diagram
 * @param chainIndex 0-based index of the chain in the diagram
 * @param lineNumber 1-based line the chain starts on, or {@code 0} for
 *                   the implicit chain
 * @param steps      the steps inside the chain, in document order
 */
public record DiagramChain(String label, int chainIndex, int lineNumber, List<FlowStep> steps) {

    /**
     * Defensively copies the step list.
     *
     * @param label      the entry point naming this chain
     * @param chainIndex 0-based chain index
     * @param lineNumber 1-based start line
     * @param steps      the steps inside the chain
     */
    public DiagramChain {
        steps = List.copyOf(steps);
    }

    /**
     * Split a parsed diagram into its chains.
     *
     * @param steps the steps of the whole diagram; must not be {@code null}
     * @return the chains in document order; never {@code null}, never
     *         empty — a diagram without block markers yields one
     *         implicit chain
     */
    public static List<DiagramChain> of(List<FlowStep> steps) {
        List<DiagramChain> chains = new ArrayList<>();
        List<FlowStep> current = new ArrayList<>();
        String label = "";
        int chainIndex = 0;
        int lineNumber = 0;
        boolean insideChain = false;

        for (FlowStep step : steps) {
            switch (step.kind()) {
                case PARTICIPANT:
                case TITLE:
                    continue;
                case CHAIN_START:
                    insideChain = true;
                    label = "";
                    chainIndex = step.chainIndex();
                    lineNumber = step.lineNumber();
                    current = new ArrayList<>();
                    continue;
                case CHAIN_NOTE:
                    label = step.label();
                    continue;
                case CHAIN_END:
                    chains.add(new DiagramChain(label, chainIndex, lineNumber, current));
                    insideChain = false;
                    current = new ArrayList<>();
                    continue;
                default:
                    current.add(step);
            }
        }
        if (insideChain || chains.isEmpty()) {
            // an unclosed block, or a diagram that never opened one: the remainder
            // is the implicit chain, so a single-chain diagram needs no special case
            chains.add(new DiagramChain(label, chainIndex, lineNumber, current));
        }
        return List.copyOf(chains);
    }
}
