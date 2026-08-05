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
 * PlantUML — claimed for {@code .puml}, {@code .plantuml} and
 * {@code .iuml}.
 *
 * <p>Selected by nothing but the expected file's extension: the same
 * recording compared against a {@code .puml} file is rendered as
 * PlantUML, against a {@code .mmd} file as Mermaid. The recorder's
 * own {@code cdi-flow.output-format} plays no part in it.
 *
 * <p>Registered at {@code @Priority(Integer.MAX_VALUE)} so any custom
 * dialect claiming the same extension outranks it.
 */
@Priority(Integer.MAX_VALUE)
public class PlantUmlFlowDialect implements FlowDialect {

    /** No-arg constructor required by SPI {@code ServiceLoader} lookup. */
    public PlantUmlFlowDialect() {
    }

    @Override
    public String name() {
        return "plantuml";
    }

    @Override
    public Set<String> fileExtensions() {
        return Set.of(".puml", ".plantuml", ".iuml");
    }

    @Override
    public String render(List<CallFlow> flows) {
        return CombinedFlowDiagram.of(flows, DiagramFormat.PLANTUML, null);
    }

    @Override
    public String renderSingle(CallFlow flow) {
        return flow.toPlantUml();
    }

    @Override
    public List<FlowStep> parse(String diagram) {
        return SequenceDiagramParser.parsePlantUml(diagram);
    }
}
