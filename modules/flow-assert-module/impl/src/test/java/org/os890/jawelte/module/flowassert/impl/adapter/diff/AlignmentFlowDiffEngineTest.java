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
package org.os890.jawelte.module.flowassert.impl.adapter.diff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.os890.cdi.uml.dynamic.flow.renderer.testsupport.CallNodeBuilder.call;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.os890.cdi.uml.dynamic.flow.renderer.api.CallFlow;
import org.os890.jawelte.module.flowassert.api.FlowDiff;
import org.os890.jawelte.module.flowassert.api.FlowStep;
import org.os890.jawelte.module.flowassert.impl.adapter.dialect.MermaidFlowDialect;

class AlignmentFlowDiffEngineTest {

    private final AlignmentFlowDiffEngine engine = new AlignmentFlowDiffEngine();

    private final MermaidFlowDialect dialect = new MermaidFlowDialect();

    private static FlowDiff.DiffSpec spec() {
        return new FlowDiff.DiffSpec(List.of(), List.of(), List.of(), false, false, true, false);
    }

    private static FlowDiff.DiffSpec spec(
            List<String> ignore, List<String> ignoreSubtree, List<String> ignoreChains) {
        return new FlowDiff.DiffSpec(ignore, ignoreSubtree, ignoreChains, false, false, true, false);
    }

    private static CallFlow placeOrder(String pricingBean) {
        return call("OrderService", "placeOrder").params("String", "int").returning("Order")
                .calling(call(pricingBean, "priceOf").params("String").returning("BigDecimal"),
                        call("AuditService", "log").params("String"))
                .buildFlow();
    }

    private static CallFlow ship() {
        return call("ShippingService", "ship").params("String").returning("void").buildFlow();
    }

    private List<FlowStep> combined(CallFlow... flows) {
        return dialect.parse(dialect.render(List.of(flows)));
    }

    private List<FlowDiff.Difference> diff(List<FlowStep> expected, List<FlowStep> actual) {
        return engine.diff(expected, actual, spec());
    }

    @Test
    @DisplayName("the same recording twice is no difference at all")
    void identicalRecordingsMatch() {
        assertThat(diff(combined(placeOrder("PricingService")),
                combined(placeOrder("PricingService")))).isEmpty();
    }

    @Test
    @DisplayName("two runs of the same flow differ in their timings and still match")
    void timingsDoNotParticipate() {
        CallFlow fast = call("OrderService", "placeOrder").returning("Order").nanos(0, 1_000_000)
                .buildFlow();
        CallFlow slow = call("OrderService", "placeOrder").returning("Order").nanos(0, 90_000_000)
                .buildFlow();

        assertThat(diff(combined(fast), combined(slow))).isEmpty();
    }

    @Test
    @DisplayName("the same call to another bean is one DIFFERENT_TARGET, not a delete plus an insert")
    void reportsADifferentCallee() {
        List<FlowDiff.Difference> differences = diff(
                combined(placeOrder("PricingService")), combined(placeOrder("DiscountService")));

        assertThat(differences).filteredOn(difference ->
                        difference.kind() == FlowDiff.Difference.Kind.DIFFERENT_TARGET)
                .singleElement()
                .satisfies(difference -> {
                    assertThat(difference.expected()).contains("PricingService", "priceOf(String)");
                    assertThat(difference.actual()).contains("DiscountService", "priceOf(String)");
                    assertThat(difference.expectedLineNumber()).isPositive();
                    assertThat(difference.depth()).isEqualTo(1);
                });
    }

    @Test
    @DisplayName("a call the recording never made is a MISSING_CALL on its expected line")
    void reportsAMissingCall() {
        List<FlowStep> expected = combined(placeOrder("PricingService"));
        List<FlowStep> actual = combined(call("OrderService", "placeOrder")
                .params("String", "int").returning("Order")
                .calling(call("PricingService", "priceOf").params("String").returning("BigDecimal"))
                .buildFlow());

        assertThat(diff(expected, actual)).filteredOn(difference ->
                        difference.kind() == FlowDiff.Difference.Kind.MISSING_CALL)
                .anySatisfy(difference -> {
                    assertThat(difference.expected()).contains("AuditService", "log(String)");
                    assertThat(difference.actual()).isEqualTo(FlowDiff.Difference.MISSING);
                    assertThat(difference.expectedLineNumber()).isPositive();
                });
    }

    @Test
    @DisplayName("an outermost call too many is one UNEXPECTED_CHAIN, not a shifted diagram")
    void reportsAnExtraChain() {
        List<FlowDiff.Difference> differences = diff(
                combined(placeOrder("PricingService")),
                combined(placeOrder("PricingService"), ship()));

        assertThat(differences).filteredOn(difference ->
                        difference.kind() == FlowDiff.Difference.Kind.UNEXPECTED_CHAIN)
                .singleElement()
                .satisfies(difference -> assertThat(difference.actual()).contains("ShippingService.ship"));
        assertThat(differences).filteredOn(difference ->
                difference.kind() == FlowDiff.Difference.Kind.MISSING_CALL).isEmpty();
    }

    @Test
    @DisplayName("a chain the recording never drove is one MISSING_CHAIN")
    void reportsAMissingChain() {
        assertThat(diff(combined(placeOrder("PricingService"), ship()),
                combined(placeOrder("PricingService"))))
                .filteredOn(difference -> difference.kind() == FlowDiff.Difference.Kind.MISSING_CHAIN)
                .singleElement()
                .satisfies(difference -> assertThat(difference.expected()).contains("ShippingService.ship"));
    }

    @Test
    @DisplayName("the same two calls in the other order are one WRONG_ORDER")
    void reportsReordering() {
        CallFlow expectedOrder = call("OrderService", "placeOrder").returning("Order")
                .calling(call("PricingService", "priceOf").returning("BigDecimal"),
                        call("InventoryService", "reserve").returning("boolean"))
                .buildFlow();
        CallFlow actualOrder = call("OrderService", "placeOrder").returning("Order")
                .calling(call("InventoryService", "reserve").returning("boolean"),
                        call("PricingService", "priceOf").returning("BigDecimal"))
                .buildFlow();

        assertThat(diff(combined(expectedOrder), combined(actualOrder)))
                .filteredOn(difference -> difference.kind() == FlowDiff.Difference.Kind.WRONG_ORDER)
                .isNotEmpty();
    }

    @Test
    @DisplayName("a loop that ran a different number of times is a LOOP_COUNT")
    void reportsLoopCounts() {
        CallFlow twice = call("BatchService", "processAll").returning("int")
                .calling(call("ItemValidator", "validate").returning("boolean"),
                        call("ItemValidator", "validate").returning("boolean"))
                .buildFlow();
        CallFlow thrice = call("BatchService", "processAll").returning("int")
                .calling(call("ItemValidator", "validate").returning("boolean"),
                        call("ItemValidator", "validate").returning("boolean"),
                        call("ItemValidator", "validate").returning("boolean"))
                .buildFlow();

        assertThat(diff(combined(twice), combined(thrice)))
                .filteredOn(difference -> difference.kind() == FlowDiff.Difference.Kind.LOOP_COUNT)
                .singleElement()
                .satisfies(difference -> {
                    assertThat(difference.expected()).isEqualTo("loop 2 times");
                    assertThat(difference.actual()).isEqualTo("loop 3 times");
                });
    }

    @Test
    @DisplayName("an ignored call is left out of both sides, its return with it")
    void ignoresCallsByPattern() {
        List<FlowStep> withAudit = combined(placeOrder("PricingService"));
        List<FlowStep> withoutAudit = combined(call("OrderService", "placeOrder")
                .params("String", "int").returning("Order")
                .calling(call("PricingService", "priceOf").params("String").returning("BigDecimal"))
                .buildFlow());

        assertThat(engine.diff(withAudit, withoutAudit,
                spec(List.of("AuditService#log(*)"), List.of(), List.of())))
                .filteredOn(difference -> difference.kind() != FlowDiff.Difference.Kind.MISSING_PARTICIPANT)
                .isEmpty();
    }

    @Test
    @DisplayName("an ignored subtree takes what it called with it")
    void ignoresSubtreesByPattern() {
        CallFlow withNestedPricing = call("OrderService", "placeOrder").returning("Order")
                .calling(call("PricingService", "priceOf").returning("BigDecimal")
                        .calling(call("TaxService", "taxFor").returning("BigDecimal")))
                .buildFlow();
        CallFlow withoutPricing = call("OrderService", "placeOrder").returning("Order").buildFlow();

        assertThat(engine.diff(combined(withNestedPricing), combined(withoutPricing),
                spec(List.of(), List.of("PricingService#priceOf()"), List.of())))
                .filteredOn(difference -> difference.kind() != FlowDiff.Difference.Kind.MISSING_PARTICIPANT)
                .isEmpty();
    }

    @Test
    @DisplayName("an ignored chain is left out of the comparison entirely")
    void ignoresChainsByEntryPoint() {
        assertThat(engine.diff(combined(placeOrder("PricingService")),
                combined(placeOrder("PricingService"), ship()),
                spec(List.of(), List.of(), List.of("ShippingService.*"))))
                .filteredOn(difference -> difference.kind() != FlowDiff.Difference.Kind.UNEXPECTED_PARTICIPANT)
                .isEmpty();
    }

    @Test
    @DisplayName("a participant lane on one side only is reported as such")
    void reportsParticipantDifferences() {
        List<FlowDiff.Difference> differences = diff(
                combined(placeOrder("PricingService")), combined(placeOrder("DiscountService")));

        assertThat(differences).extracting(FlowDiff.Difference::kind)
                .contains(FlowDiff.Difference.Kind.MISSING_PARTICIPANT,
                        FlowDiff.Difference.Kind.UNEXPECTED_PARTICIPANT);
    }

    @Test
    @DisplayName("a single-chain diagram needs no block markers to be compared")
    void comparesSingleChainDiagrams() {
        CallFlow flow = placeOrder("PricingService");

        assertThat(engine.diff(dialect.parse(dialect.renderSingle(flow)),
                dialect.parse(dialect.renderSingle(flow)), spec())).isEmpty();
    }
}
