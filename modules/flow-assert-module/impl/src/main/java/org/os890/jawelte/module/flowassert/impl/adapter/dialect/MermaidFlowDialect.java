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
package org.os890.jawelte.module.flowassert.impl.adapter.dialect;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.annotation.Priority;

import org.os890.cdi.uml.dynamic.flow.renderer.api.CallFlow;
import org.os890.cdi.uml.dynamic.flow.renderer.config.DiagramFormat;
import org.os890.cdi.uml.dynamic.flow.renderer.report.CombinedFlowDiagram;
import org.os890.jawelte.module.flowassert.api.FlowStep;
import org.os890.jawelte.module.flowassert.api.port.FlowDialect;
import org.os890.jawelte.module.flowassert.impl.util.SequenceDiagramParser;

/**
 * Mermaid — the default notation, claimed for {@code .mmd} and
 * {@code .mermaid}.
 *
 * <p>Renders through the recorder itself
 * ({@code CombinedFlowDiagram.of(...)} for the combined diagram,
 * {@code CallFlow#toMermaid()} for a single chain), so what an
 * assertion compares is byte-for-byte what the recorder would have
 * written to a file — and a {@code use-case.mmd} taken from a real
 * application run is a valid expected file.
 *
 * <p>No title is rendered: the comparison ignores titles by default,
 * and a diagram with none is the smaller surface.
 *
 * <p>Registered at {@code @Priority(Integer.MAX_VALUE)} so any custom
 * dialect claiming the same extension outranks it.
 */
@Priority(Integer.MAX_VALUE)
public class MermaidFlowDialect implements FlowDialect {

    /** Ordered: {@code .mmd} is the canonical extension a created file gets. */
    private static final Set<String> EXTENSIONS =
            Collections.unmodifiableSet(new LinkedHashSet<>(List.of(".mmd", ".mermaid")));

    /** No-arg constructor required by SPI {@code ServiceLoader} lookup. */
    public MermaidFlowDialect() {
    }

    @Override
    public String name() {
        return "mermaid";
    }

    @Override
    public Set<String> fileExtensions() {
        return EXTENSIONS;
    }

    @Override
    public String render(List<CallFlow> flows) {
        return CombinedFlowDiagram.of(flows, DiagramFormat.MERMAID, null);
    }

    @Override
    public String renderSingle(CallFlow flow) {
        return untitled(flow).toMermaid();
    }

    /**
     * Drops the use-case label the lifecycle adapter set, so a
     * single-chain diagram carries no title either. A title naming the
     * test method would make an otherwise reusable expected file
     * specific to one of them, and the comparison ignores titles by
     * default anyway.
     */
    private static CallFlow untitled(CallFlow flow) {
        return new CallFlow(flow.root(), flow.threadName(), flow.config());
    }

    @Override
    public List<FlowStep> parse(String diagram) {
        return SequenceDiagramParser.parseMermaid(diagram);
    }
}
