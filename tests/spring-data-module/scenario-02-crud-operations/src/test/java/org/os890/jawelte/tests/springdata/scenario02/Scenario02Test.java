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
package org.os890.jawelte.tests.springdata.scenario02;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/** Scenario 2 — CRUD operations against H2 via the auto-registered repository. */
@EnableTestBeans
public class Scenario02Test {

    @Inject
    private CrudInvoker crudInvoker;

    /** No-arg constructor for CDI. */
    public Scenario02Test() {
    }

    /** save assigns an id; findById returns the saved row; deleteById removes it. */
    @Test
    public void saveFindByIdDeleteByIdRoundTrip() {
        Long savedId = crudInvoker.save("Alice");
        assertThat(savedId)
                .as("repository.save must dispatch through the EntityManager and assign a generated id")
                .isNotNull();

        Optional<Customer> retrieved = crudInvoker.findById(savedId);
        assertThat(retrieved)
                .as("findById on the same id must return the saved row")
                .isPresent();
        assertThat(retrieved.get().getName())
                .as("retrieved entity carries the saved name")
                .isEqualTo("Alice");

        crudInvoker.deleteById(savedId);
        assertThat(crudInvoker.findById(savedId))
                .as("deleteById must remove the row from the database")
                .isEmpty();
    }

    /** Bridges the test method's invocations through a transactional bean. */
    @ApplicationScoped
    public static class CrudInvoker {

        @Inject
        private CustomerRepository customerRepository;

        /** No-arg constructor for CDI. */
        public CrudInvoker() {
        }

        /**
         * Save a customer and return the assigned id.
         *
         * @param name the customer's name
         * @return the persisted id
         */
        @Transactional
        public Long save(String name) {
            Customer saved = customerRepository.save(new Customer(name));
            return saved.getId();
        }

        /**
         * Look up a customer by primary key.
         *
         * @param id the primary key
         * @return the customer, if present
         */
        @Transactional
        public Optional<Customer> findById(Long id) {
            return customerRepository.findById(id);
        }

        /**
         * Delete a customer by primary key.
         *
         * @param id the primary key
         */
        @Transactional
        public void deleteById(Long id) {
            customerRepository.deleteById(id);
        }
    }
}
