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
package org.os890.jawelte.tests.flowassert.scenario04;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.flowassert.api.EnableFlowAssert;
import org.os890.jawelte.module.flowassert.api.ExpectedFlow;
import org.os890.jawelte.module.flowassert.api.RecordedFlows;

/**
 * Two outermost calls in one test method. Each one is a flow of its
 * own - a chain ends when its outermost call returns - and what the
 * assertion compares is the combined diagram: one block per chain, in
 * the order they happened, sharing the participant lanes.
 *
 * <p>This is the shape that makes the combined diagram the default
 * unit of comparison rather than a single chain: a test that drives
 * two use-cases pins both of them, and the second one cannot quietly
 * disappear.
 */
@EnableFlowAssert
class Scenario04Test {

    @Inject
    private OrderService orderService;

    @Inject
    private ShippingService shippingService;

    @Test
    @ExpectedFlow
    void placesAnOrderAndShipsIt() {
        orderService.placeOrder("SKU-1", 2);
        shippingService.ship("SKU-1");

        assertThat(RecordedFlows.all()).hasSize(2);
        assertThat(RecordedFlows.all()).extracting(flow -> flow.entryTypeSimpleName())
                .containsExactly("OrderService", "ShippingService");
        assertThat(RecordedFlows.byEntryPoint(ShippingService.class)).isPresent();
    }

    @Test
    void aChainCanBeLeftOutOfTheComparison() {
        orderService.placeOrder("SKU-1", 2);
        shippingService.ship("SKU-1");

        // the expected file of placesAnOrderAndShipsIt holds both chains; ignoring the
        // shipping one makes it match a recording of the order alone
        org.os890.jawelte.module.flowassert.api.FlowDiff.forRecordedFlows()
                .expected("flows/order-only.mmd")
                .ignoringChains("ShippingService.*")
                .assertEquals();
    }
}
