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
package org.os890.jawelte.tests.jpa.scenario46;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;

/** Persist + query M:1 relationship; verify per-method cleanup wipes both tables. */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Scenario46Test {

    @Inject
    private CustomerOrderService customerOrderService;

    /** No-arg constructor for CDI. */
    public Scenario46Test() {
    }

    /** First method: persist parent + child, walk relationship back. */
    @Test
    @Order(1)
    public void persistAndTraverseRelationship() {
        Long orderId = customerOrderService.createCustomerWithOrder("Alice", "Widget x10");
        assertThat(orderId).isNotNull();

        String customerName = customerOrderService.findCustomerNameForOrder(orderId);
        assertThat(customerName).isEqualTo("Alice");

        assertThat(customerOrderService.countCustomers()).isEqualTo(1L);
        assertThat(customerOrderService.countOrders()).isEqualTo(1L);
    }

    /** Second method: cleanup wiped both tables before this method ran. */
    @Test
    @Order(2)
    public void perMethodCleanupWipesBothTables() {
        assertThat(customerOrderService.countCustomers()).isEqualTo(0L);
        assertThat(customerOrderService.countOrders()).isEqualTo(0L);
    }
}
