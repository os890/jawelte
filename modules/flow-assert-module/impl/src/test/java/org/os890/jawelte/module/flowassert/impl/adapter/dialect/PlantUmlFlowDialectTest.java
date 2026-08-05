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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.os890.cdi.uml.dynamic.flow.renderer.testsupport.CallNodeBuilder.call;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.os890.cdi.uml.dynamic.flow.renderer.api.CallFlow;
import org.os890.jawelte.module.flowassert.api.FlowStep;

class PlantUmlFlowDialectTest {

    private final PlantUmlFlowDialect dialect = new PlantUmlFlowDialect();

    private final MermaidFlowDialect mermaid = new MermaidFlowDialect();

    private static CallFlow placeOrderFlow() {
        return call("OrderService", "placeOrder").params("String", "int").returning("Order")
                .calling(call("PricingService", "priceOf").params("String").returning("BigDecimal"))
                .buildFlow();
    }

    private static CallFlow shipFlow() {
        return call("ShippingService", "ship").params("String").returning("void").buildFlow();
    }

    @Test
    @DisplayName("claims the three PlantUML extensions")
    void claimsItsExtensions() {
        assertThat(dialect.name()).isEqualTo("plantuml");
        assertThat(dialect.fileExtensions()).containsExactlyInAnyOrder(".puml", ".plantuml", ".iuml");
    }

    @Test
    @DisplayName("several flows render as one diagram, one group each")
    void rendersCombinedDiagram() {
        String diagram = dialect.render(List.of(placeOrderFlow(), shipFlow()));

        assertThat(diagram)
                .startsWith("@startuml")
                .endsWith("@enduml\n")
                .contains("group OrderService.placeOrder")
                .contains("group ShippingService.ship")
                .doesNotContain("->>");
    }

    @Test
    @DisplayName("what it renders it reads back, down to the same steps Mermaid yields")
    void parsesTheSameStructureAsMermaid() {
        List<CallFlow> flows = List.of(placeOrderFlow(), shipFlow());

        List<FlowStep> plantUmlSteps = dialect.parse(dialect.render(flows));
        List<FlowStep> mermaidSteps = mermaid.parse(mermaid.render(flows));

        assertThat(structureOf(plantUmlSteps)).isEqualTo(structureOf(mermaidSteps));
        assertThat(plantUmlSteps).filteredOn(step -> step.kind() == FlowStep.Kind.PARTICIPANT)
                .extracting(FlowStep::label)
                .containsExactly("\"caller\" as Caller", "OrderService", "PricingService",
                        "ShippingService");
        assertThat(plantUmlSteps).filteredOn(step -> step.kind() == FlowStep.Kind.CALL)
                .extracting(FlowStep::to, FlowStep::label, FlowStep::depth)
                .containsExactly(
                        tuple("OrderService", "placeOrder(String, int)", 0),
                        tuple("PricingService", "priceOf(String)", 1),
                        tuple("ShippingService", "ship(String)", 0));
    }

    @Test
    @DisplayName("the block note of a single chain is volatile, and marked as such")
    void classifiesTheBlockNoteAsHeaderNote() {
        List<FlowStep> steps = dialect.parse(dialect.renderSingle(placeOrderFlow()));

        assertThat(steps).filteredOn(step -> step.kind() == FlowStep.Kind.HEADER_NOTE)
                .singleElement()
                .satisfies(step -> assertThat(step.annotation()).contains("thread main"));
    }

    @Test
    @DisplayName("an event arrow and a thrown type survive the round trip")
    void parsesEventsAndFailures() {
        CallFlow flow = call("CheckoutService", "checkout")
                .calling(call("StockObserver", "onCheckout").params("CheckoutEvent").asObserver(),
                        call("FailingService", "fail").throwing(new IllegalStateException("boom")))
                .buildFlow();

        List<FlowStep> steps = dialect.parse(dialect.renderSingle(flow));

        assertThat(steps).filteredOn(step -> step.kind() == FlowStep.Kind.EVENT)
                .singleElement()
                .satisfies(step -> assertThat(step.label()).isEqualTo("[event] onCheckout(CheckoutEvent)"));
        assertThat(steps).filteredOn(step -> step.kind() == FlowStep.Kind.THROW)
                .anySatisfy(step -> assertThat(step.label()).isEqualTo("throws IllegalStateException"));
    }

    @Test
    @DisplayName("a line that is not PlantUML fails loudly rather than being ignored")
    void rejectsUnreadableLines() {
        assertThatThrownBy(() -> dialect.parse("@startuml\nOrderService => PricingService\n@enduml\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line 2")
                .hasMessageContaining("PlantUML");
    }

    /**
     * kind, participants and label of every step - everything but the line numbers.
     * Participant declarations are left out: how a lane is declared is
     * notation-specific text ({@code Caller as caller} versus
     * {@code "caller" as Caller}), and a comparison only ever sees one notation.
     */
    private static List<String> structureOf(List<FlowStep> steps) {
        return steps.stream()
                .filter(step -> step.kind() != FlowStep.Kind.HEADER)
                .filter(step -> step.kind() != FlowStep.Kind.HEADER_NOTE)
                .filter(step -> step.kind() != FlowStep.Kind.PARTICIPANT)
                .map(step -> step.kind() + "|" + step.from() + "|" + step.to() + "|" + step.label()
                        + "|" + step.chainIndex() + "|" + step.depth())
                .toList();
    }
}
