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
package org.os890.jawelte.tests.springdata.scenario14;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Scenario 14 — two different repository interfaces on two different
 * entities. Both auto-discovered, both injectable, both functional.
 */
@EnableTestBeans
public class Scenario14Test {

    @Inject
    private MultiInvoker multiInvoker;

    /** No-arg constructor for CDI. */
    public Scenario14Test() {
    }

    /** Customer and Order rows persisted via their respective repositories. */
    @Test
    public void bothRepositoriesPersistAndQueryIndependently() {
        Long customerId = multiInvoker.saveCustomer("Alice");
        Long orderId = multiInvoker.saveOrder("widget-x42");

        assertThat(customerId)
                .as("CustomerRepository persists and assigns an id")
                .isNotNull();
        assertThat(orderId)
                .as("OrderRepository persists and assigns an id")
                .isNotNull();

        assertThat(multiInvoker.customerCount())
                .as("the Customer table holds the one row CustomerRepository persisted")
                .isEqualTo(1L);
        assertThat(multiInvoker.orderCount())
                .as("the Orders table holds the one row OrderRepository persisted")
                .isEqualTo(1L);
    }

    /** Transactional bridge that uses both repositories. */
    @ApplicationScoped
    public static class MultiInvoker {

        @Inject
        private CustomerRepository customerRepository;

        @Inject
        private OrderRepository orderRepository;

        /** No-arg constructor for CDI. */
        public MultiInvoker() {
        }

        /**
         * Save a customer.
         *
         * @param name the customer's name
         * @return the assigned id
         */
        @Transactional
        public Long saveCustomer(String name) {
            return customerRepository.save(new Customer(name)).getId();
        }

        /**
         * Save an order.
         *
         * @param description the order description
         * @return the assigned id
         */
        @Transactional
        public Long saveOrder(String description) {
            return orderRepository.save(new Order(description)).getId();
        }

        /**
         * Customer count.
         *
         * @return total customer rows
         */
        @Transactional
        public long customerCount() {
            return customerRepository.count();
        }

        /**
         * Order count.
         *
         * @return total order rows
         */
        @Transactional
        public long orderCount() {
            return orderRepository.count();
        }
    }
}
