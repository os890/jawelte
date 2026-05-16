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
package org.os890.jawelte.tests.springdata.scenario13;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Scenario 13 — paging and sorting via `findAll(PageRequest)` returns the requested slice. */
@EnableTestBeans
public class Scenario13Test {

    @Inject
    private DataInvoker dataInvoker;

    /** No-arg constructor for CDI. */
    public Scenario13Test() {
    }

    /** Paging returns the right slice in the right order. */
    @Test
    public void firstPageOfTwoSortedByNameAscending() {
        dataInvoker.saveAll("Carol", "Alice", "Bob", "Dave");

        Page<Customer> page = dataInvoker.firstPageOfTwoByNameAsc();
        assertThat(page.getTotalElements())
                .as("total row count reflects all four persisted rows")
                .isEqualTo(4L);
        assertThat(page.getContent())
                .as("first page of size 2 sorted by name ascending returns Alice then Bob")
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
         * Save the given names.
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
         * First page of size 2 sorted by name ascending.
         *
         * @return the page
         */
        @Transactional
        public Page<Customer> firstPageOfTwoByNameAsc() {
            return customerRepository.findAll(PageRequest.of(0, 2, Sort.by("name").ascending()));
        }
    }
}
