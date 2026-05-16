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
package org.os890.jawelte.tests.springdata.scenario03;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/** Scenario 3 — derived-query methods (`findByName`) on the auto-discovered repository. */
@EnableTestBeans
public class Scenario03Test {

    @Inject
    private DataInvoker dataInvoker;

    /** No-arg constructor for CDI. */
    public Scenario03Test() {
    }

    /** Spring Data parses the method name and queries by the named field. */
    @Test
    public void derivedQueryReturnsMatchingRowsAndEmptyOnMiss() {
        dataInvoker.saveAll("Alice", "Bob", "Alice");

        List<Customer> alices = dataInvoker.findByName("Alice");
        assertThat(alices)
                .as("two customers named 'Alice' were persisted; derived query returns both")
                .hasSize(2)
                .allSatisfy(c -> assertThat(c.getName()).isEqualTo("Alice"));

        List<Customer> nobody = dataInvoker.findByName("Eve");
        assertThat(nobody)
                .as("no customer named 'Eve' was persisted; derived query returns empty list")
                .isEmpty();
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
         * Save all the given names.
         *
         * @param names names to save in order
         */
        @Transactional
        public void saveAll(String... names) {
            for (String name : names) {
                customerRepository.save(new Customer(name));
            }
        }

        /**
         * Look up customers by exact name.
         *
         * @param name the name to match
         * @return matching customers
         */
        @Transactional
        public List<Customer> findByName(String name) {
            return customerRepository.findByName(name);
        }
    }
}
