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

class MermaidFlowDialectTest {

    private final MermaidFlowDialect dialect = new MermaidFlowDialect();

    private static CallFlow placeOrderFlow() {
        return call("OrderService", "placeOrder").params("String", "int").returning("Order")
                .calling(call("PricingService", "priceOf").params("String").returning("BigDecimal"))
                .buildFlow();
    }

    private static CallFlow shipFlow() {
        return call("ShippingService", "ship").params("String").returning("void").buildFlow();
    }

    @Test
    @DisplayName("claims the two Mermaid extensions")
    void claimsItsExtensions() {
        assertThat(dialect.name()).isEqualTo("mermaid");
        assertThat(dialect.fileExtensions()).containsExactlyInAnyOrder(".mmd", ".mermaid");
    }

    @Test
    @DisplayName("a single flow renders without the combined-diagram wrapper")
    void rendersSingleFlow() {
        String diagram = dialect.renderSingle(placeOrderFlow());

        assertThat(diagram)
                .startsWith("sequenceDiagram")
                .contains("Caller->>OrderService: placeOrder(String, int)")
                .doesNotContain("rect rgb");
    }

    @Test
    @DisplayName("several flows render as one diagram, one block each")
    void rendersCombinedDiagram() {
        String diagram = dialect.render(List.of(placeOrderFlow(), shipFlow()));

        assertThat(diagram)
                .contains("rect rgb(244, 244, 244)")
                .contains("OrderService.placeOrder")
                .contains("ShippingService.ship");
    }

    @Test
    @DisplayName("what it renders it reads back: calls, returns, chains and depths")
    void parsesWhatItRenders() {
        List<FlowStep> steps = dialect.parse(dialect.render(List.of(placeOrderFlow(), shipFlow())));

        assertThat(steps).extracting(FlowStep::kind)
                .contains(FlowStep.Kind.HEADER, FlowStep.Kind.PARTICIPANT, FlowStep.Kind.CHAIN_START,
                        FlowStep.Kind.CHAIN_NOTE, FlowStep.Kind.CALL, FlowStep.Kind.RETURN,
                        FlowStep.Kind.CHAIN_END);
        assertThat(steps).filteredOn(step -> step.kind() == FlowStep.Kind.CHAIN_NOTE)
                .extracting(FlowStep::label)
                .containsExactly("OrderService.placeOrder", "ShippingService.ship");
        assertThat(steps).filteredOn(step -> step.kind() == FlowStep.Kind.CHAIN_START).hasSize(2);
        assertThat(steps).filteredOn(step -> step.kind() == FlowStep.Kind.CALL)
                .extracting(FlowStep::to, FlowStep::label, FlowStep::depth)
                .containsExactly(
                        tuple("OrderService", "placeOrder(String, int)", 0),
                        tuple("PricingService", "priceOf(String)", 1),
                        tuple("ShippingService", "ship(String)", 0));
    }

    @Test
    @DisplayName("a duration is split off the label, so ignoring timings needs no notation knowledge")
    void splitsTimingsIntoTheAnnotation() {
        CallFlow flow = call("OrderService", "placeOrder").returning("Order")
                .nanos(0, 5_000_000).buildFlow();

        List<FlowStep> steps = dialect.parse(dialect.renderSingle(flow));

        assertThat(steps).filteredOn(step -> step.kind() == FlowStep.Kind.RETURN)
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.label()).isEqualTo("Order");
                    assertThat(step.annotation()).isEqualTo("5.00 ms");
                });
    }

    @Test
    @DisplayName("the prologue note of a single chain is volatile, and marked as such")
    void classifiesThePrologueNoteAsHeaderNote() {
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
    @DisplayName("a folded loop keeps its iteration count")
    void parsesLoopCounts() {
        CallFlow flow = call("BatchService", "processAll").returning("int")
                .calling(call("ItemValidator", "validate").params("String").returning("boolean"),
                        call("ItemValidator", "validate").params("String").returning("boolean"),
                        call("ItemValidator", "validate").params("String").returning("boolean"))
                .buildFlow();

        List<FlowStep> steps = dialect.parse(dialect.renderSingle(flow));

        assertThat(steps).filteredOn(step -> step.kind() == FlowStep.Kind.LOOP_START)
                .singleElement()
                .satisfies(step -> assertThat(step.label()).isEqualTo("3"));
        assertThat(steps).filteredOn(step -> step.kind() == FlowStep.Kind.LOOP_END).hasSize(1);
    }

    @Test
    @DisplayName("a line that is not Mermaid fails loudly rather than being ignored")
    void rejectsUnreadableLines() {
        assertThatThrownBy(() -> dialect.parse("sequenceDiagram\n    OrderService >> PricingService\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line 2")
                .hasMessageContaining("Mermaid");
    }

    @Test
    @DisplayName("a hotspot marker is recognized as one, not as a note")
    void parsesHotspotNotes() {
        List<FlowStep> steps = dialect.parse("sequenceDiagram\n"
                + "    participant Caller as caller\n"
                + "    participant ReportRepository\n"
                + "    Caller->>ReportRepository: loadRows()\n"
                + "    ReportRepository-->>Caller: int [125.1 ms]\n"
                + "    Note over ReportRepository: HOTSPOT ReportRepository.loadRows took 125.1 ms (over 50 ms)\n");

        assertThat(steps).filteredOn(step -> step.kind() == FlowStep.Kind.HOTSPOT)
                .singleElement()
                .satisfies(step -> assertThat(step.label()).startsWith("HOTSPOT ReportRepository.loadRows"));
    }
}
