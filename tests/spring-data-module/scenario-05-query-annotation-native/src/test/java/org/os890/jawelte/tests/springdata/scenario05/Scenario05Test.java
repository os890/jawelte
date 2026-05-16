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
package org.os890.jawelte.tests.springdata.scenario05;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/** Scenario 5 — `@Query(nativeQuery=true)` executes native SQL against H2. */
@EnableTestBeans
public class Scenario05Test {

    @Inject
    private DataInvoker dataInvoker;

    /** No-arg constructor for CDI. */
    public Scenario05Test() {
    }

    /** Native SQL via `@Query(nativeQuery=true)` returns customer rows in id order. */
    @Test
    public void nativeQueryReturnsAllRows() {
        dataInvoker.saveAll("Alice", "Bob");

        List<Customer> rows = dataInvoker.findAllNative();
        assertThat(rows)
                .as("native SELECT * returns both persisted rows")
                .hasSize(2)
                .extracting(Customer::getName)
                .containsExactly("Alice", "Bob");
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
         * Native query reading every CUSTOMER row.
         *
         * @return all rows
         */
        @Transactional
        public List<Customer> findAllNative() {
            return customerRepository.findAllNative();
        }
    }
}
