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
package org.os890.jawelte.tests.flowassert.scenario05;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.flowassert.api.EnableFlowAssert;
import org.os890.jawelte.module.flowassert.api.ExpectedFlow;
import org.os890.jawelte.module.flowassert.api.FlowDiff;
import org.os890.jawelte.module.flowassert.api.RecordedFlows;

/**
 * A named expected file plus an ignored collaborator: the audit call
 * is recorded but left out of the comparison, so the checked-in
 * diagram does not carry it. A test that cares about the pricing path
 * says so, and a later audit call added elsewhere does not break it.
 *
 * <p>The include-pattern narrows the recording to this package, which
 * is what an application with more beans than one test cares about
 * would do.
 */
@EnableFlowAssert(include = "org\\.os890\\.jawelte\\.tests\\.flowassert\\.scenario05\\..*")
class Scenario05Test {

    @Inject
    private OrderService orderService;

    @Test
    @ExpectedFlow(value = "flows/order-without-audit.mmd", ignoring = "AuditService#log(*)")
    void placesOrderWithoutPinningTheAudit() {
        assertThat(orderService.placeOrder("SKU-1", 2)).isEqualTo("SKU-1@5");

        // the call IS recorded - it is only left out of the comparison
        assertThat(RecordedFlows.combinedDiagram("mermaid")).contains("AuditService: log(String)");
    }

    @Test
    void theSameFileFailsWithoutTheIgnore() {
        orderService.placeOrder("SKU-1", 2);

        assertThatThrownBy(() -> FlowDiff.forRecordedFlows()
                .expected("flows/order-without-audit.mmd")
                .assertEquals())
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains("UNEXPECTED_CALL")
                        .contains("AuditService"));
    }
}
