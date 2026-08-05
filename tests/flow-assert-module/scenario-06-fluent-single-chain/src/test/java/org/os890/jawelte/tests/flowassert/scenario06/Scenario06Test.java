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
package org.os890.jawelte.tests.flowassert.scenario06;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.flowassert.api.EnableFlowAssert;
import org.os890.jawelte.module.flowassert.api.FlowDiff;
import org.os890.jawelte.module.flowassert.api.RecordedFlows;

/**
 * One chain instead of the combined diagram. A single-chain expected
 * file carries no block markers at all - it is what the recorder
 * writes per outermost call - and is selected by naming the bean the
 * chain entered through.
 *
 * <p>Also covers what happens when no chain entered through the named
 * bean: the failure lists what was recorded instead, because
 * "no recorded flow" without that list is a dead end.
 */
@EnableFlowAssert
class Scenario06Test {

    @Inject
    private OrderService orderService;

    @Test
    void comparesTheChainOfOneEntryPoint() {
        orderService.placeOrder("SKU-1", 2);

        FlowDiff.forEntryPoint(OrderService.class)
                .expected("flows/order-chain.mmd")
                .assertEquals();
        FlowDiff.forEntryPoint(OrderService.class, "placeOrder")
                .expected("flows/order-chain.mmd")
                .assertEquals();
    }

    @Test
    void comparesAFlowTheCallerHolds() {
        orderService.placeOrder("SKU-1", 2);

        FlowDiff.forFlow(RecordedFlows.single())
                .expected("flows/order-chain.mmd")
                .assertEquals();
    }

    @Test
    void namesWhatWasRecordedWhenTheEntryPointDoesNotMatch() {
        orderService.placeOrder("SKU-1", 2);

        assertThatThrownBy(() -> FlowDiff.forEntryPoint(PricingService.class)
                .expected("flows/order-chain.mmd")
                .assertEquals())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No recorded flow entered through")
                .hasMessageContaining("PricingService")
                .hasMessageContaining("Recorded: OrderService.placeOrder");
    }

    @Test
    void clearingTheRecordingKeepsSetupCallsOutOfTheComparison() {
        orderService.placeOrder("SETUP", 1);
        RecordedFlows.clear();
        orderService.placeOrder("SKU-1", 2);

        assertThat(RecordedFlows.all()).hasSize(1);
        FlowDiff.forRecordedFlows().expected("flows/order-combined.mmd").assertEquals();
    }
}
