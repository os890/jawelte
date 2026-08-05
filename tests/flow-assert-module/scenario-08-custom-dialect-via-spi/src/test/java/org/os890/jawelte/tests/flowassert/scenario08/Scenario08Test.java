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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.flowassert.api.EnableFlowAssert;
import org.os890.jawelte.module.flowassert.api.ExpectedFlow;
import org.os890.jawelte.module.flowassert.api.FlowDiff;

/**
 * A custom notation, registered through the {@code FlowDialect} SPI by
 * this test module alone. The expected file is neither Mermaid nor
 * PlantUML, and everything else - the convention-based lookup, the
 * structural comparison, the ignore lists, the failure message - works
 * unchanged.
 */
@EnableFlowAssert
class Scenario08Test {

    @Inject
    private OrderService orderService;

    @Test
    @ExpectedFlow
    void placesOrder() {
        assertThat(orderService.placeOrder("SKU-1", 2)).isEqualTo("SKU-1@5");
    }

    @Test
    void theCustomDialectRendersTheRecordingAsWell() {
        orderService.placeOrder("SKU-1", 2);

        assertThat(FlowDiff.forRecordedFlows().expected("flows/Scenario08Test/placesOrder.flow")
                .actualDiagram())
                .startsWith("chain OrderService.placeOrder")
                .contains("call OrderService>PricingService priceOf(String)");
    }

    @Test
    void aMismatchIsReportedThroughTheSharedEngine() {
        orderService.placeOrder("SKU-1", 2);

        assertThatThrownBy(() -> FlowDiff.forRecordedFlows()
                .expected("flows/wrong-callee.flow")
                .assertEquals())
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains("(text)")
                        .contains("DIFFERENT_TARGET")
                        .contains("DiscountService"));
    }
}
