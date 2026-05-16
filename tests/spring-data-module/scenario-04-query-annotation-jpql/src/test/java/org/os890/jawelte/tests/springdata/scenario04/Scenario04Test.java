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
package org.os890.jawelte.tests.springdata.scenario04;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/** Scenario 4 — `@Query(jpql)` on a repository method executes the supplied JPQL. */
@EnableTestBeans
public class Scenario04Test {

    @Inject
    private DataInvoker dataInvoker;

    /** No-arg constructor for CDI. */
    public Scenario04Test() {
    }

    /** Spring Data executes the JPQL provided in `@Query`, filtering by named parameter. */
    @Test
    public void queryAnnotationFiltersByExplicitJpql() {
        dataInvoker.persist("Alice", "ACTIVE");
        dataInvoker.persist("Bob", "INACTIVE");
        dataInvoker.persist("Carol", "ACTIVE");

        List<Customer> active = dataInvoker.findInStatus("ACTIVE");
        assertThat(active)
                .as("two ACTIVE customers were persisted; @Query JPQL must return both")
                .hasSize(2)
                .extracting(Customer::getName)
                .containsExactlyInAnyOrder("Alice", "Carol");
    }

    /** Transactional bridge through which the test invokes the repository. */
    @ApplicationScoped
    public static class DataInvoker {

        @Inject
        private CustomerRepository customerRepository;

        /** No-arg constructor for CDI. */
        public DataInvoker() {
        }

        /**
         * Save a customer with the given name and status.
         *
         * @param name the customer's name
         * @param status the customer's status
         */
        @Transactional
        public void persist(String name, String status) {
            customerRepository.save(new Customer(name, status));
        }

        /**
         * Query customers in a specific status via the repository's `@Query` method.
         *
         * @param status the status filter
         * @return matching customers
         */
        @Transactional
        public List<Customer> findInStatus(String status) {
            return customerRepository.findInStatus(status);
        }
    }
}
