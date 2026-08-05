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
package org.os890.jawelte.tests.flowassert.scenario02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.flowassert.api.EnableFlowAssert;
import org.os890.jawelte.module.flowassert.api.FlowDiff;

/**
 * What a mismatch says. The expected diagram claims the order is
 * priced by a {@code DiscountService}, while the recording shows
 * {@code PricingService} - one changed callee, on one line.
 *
 * <p>The failure has to name the kind of difference, quote both sides,
 * point at the line in the expected file, print the whole recording
 * and say where it wrote it. Anything less and the reader has to go
 * looking.
 */
@EnableFlowAssert
class Scenario02Test {

    @Inject
    private OrderService orderService;

    @Test
    void reportsTheDifferingCalleeWithItsLine() {
        orderService.placeOrder("SKU-1", 2);

        assertThatThrownBy(() -> FlowDiff.forRecordedFlows()
                .expected("flows/wrong-callee.mmd")
                .assertEquals())
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> {
                    String message = failure.getMessage();
                    assertThat(message)
                            .contains("Flow diff found")
                            .contains("(mermaid)")
                            .contains("DIFFERENT_TARGET")
                            .contains("expected line 11")
                            .contains("DiscountService")
                            .contains("PricingService")
                            .contains("recorded flow (mermaid):")
                            .contains("recorded diagram written to:");
                    assertThat(recordedDiagramPathIn(message)).exists();
                });
    }

    @Test
    void namesTheChainAndKeepsTheRestOfTheDiagramQuiet() {
        orderService.placeOrder("SKU-1", 2);

        assertThatThrownBy(() -> FlowDiff.forRecordedFlows()
                .expected("flows/wrong-callee.mmd")
                .assertEquals())
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains("[chain 1")
                        // the audit call is identical on both sides and must not be reported
                        .doesNotContain("AuditService -> ")
                        .doesNotContain("MISSING_CHAIN"));
    }

    private static Path recordedDiagramPathIn(String message) {
        String marker = "recorded diagram written to: ";
        String tail = message.substring(message.indexOf(marker) + marker.length());
        int lineEnd = tail.indexOf(System.lineSeparator());
        return Path.of(lineEnd < 0 ? tail.strip() : tail.substring(0, lineEnd).strip());
    }
}
