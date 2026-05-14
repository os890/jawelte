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
package org.os890.jawelte.tests.testcontrol.scenario08;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.testcontrol.api.TestControl;

/**
 * Scenario 08 — multi-PU testData routing. Two persistence units
 * ({@code testcontrolScenario08CustomersPU} for {@link Customer},
 * {@code testcontrolScenario08OrdersPU} for {@link Order}) are
 * declared in {@code persistence.xml}, each backed by its own H2
 * in-memory database. The test method's {@code @TestControl(testData=…)}
 * lists two entries with explicit {@code puName:} prefixes:
 * {@code testdata/scenario08-customers/dbIn/customers.xml} → CUSTOMER
 * table in the customers PU; {@code testdata/scenario08-orders/dbIn/orders.xml}
 * → CUSTOMER_ORDER table in the orders PU. The assertion side reads
 * each PU through its own {@code @PersistenceContext(unitName=…)}
 * injection and verifies the rows landed in the right database.
 *
 * <p>{@code requireDbExpected = false} because this scenario is
 * seed-only — its purpose is to prove the routing, not to verify
 * post-test state.
 */
@EnableTestBeans
class Scenario08Test {

    @Inject
    private MultiPuCountService countService;

    @Test
    @TestControl(
            testData = {
                "testcontrolScenario08CustomersPU:testdata/scenario08-customers",
                "testcontrolScenario08OrdersPU:testdata/scenario08-orders"
            },
            requireDbExpected = false)
    void multiPuTestDataRoutesEachEntryToItsOwnPersistenceUnit() {
        assertThat(countService.countCustomers())
                .as("customers PU should see the 3 customers from testdata/scenario08-customers/dbIn/")
                .isEqualTo(3);
        assertThat(countService.countOrders())
                .as("orders PU should see the 2 orders from testdata/scenario08-orders/dbIn/")
                .isEqualTo(2);
    }
}
