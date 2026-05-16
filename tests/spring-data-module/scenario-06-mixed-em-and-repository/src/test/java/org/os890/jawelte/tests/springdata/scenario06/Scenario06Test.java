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
package org.os890.jawelte.tests.springdata.scenario06;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Scenario 6 — `EntityManager` and the auto-discovered repository injected
 * into the same bean and used within a single transaction; both see the
 * same data because both dispatch through jpa-module's transaction-scoped
 * EM proxy.
 */
@EnableTestBeans
public class Scenario06Test {

    @Inject
    private MixedInvoker mixedInvoker;

    /** No-arg constructor for CDI. */
    public Scenario06Test() {
    }

    /** Write via EntityManager.persist, read via repository — same transaction, same data. */
    @Test
    public void persistViaEmReadViaRepository() {
        Long persistedId = mixedInvoker.persistAndReadBack("Alice");
        assertThat(persistedId)
                .as("EntityManager.persist must assign a generated id within the tx")
                .isNotNull();
    }

    /** {@code EntityManager} and repository in the same bean. */
    @ApplicationScoped
    public static class MixedInvoker {

        @Inject
        private EntityManager entityManager;

        @Inject
        private CustomerRepository customerRepository;

        /** No-arg constructor for CDI. */
        public MixedInvoker() {
        }

        /**
         * Persist through {@link EntityManager}, then read back through the repository
         * — both share the transaction-scoped EM and see the same data.
         *
         * @param name the customer's name
         * @return the generated id
         */
        @Transactional
        public Long persistAndReadBack(String name) {
            Customer customer = new Customer(name);
            entityManager.persist(customer);
            entityManager.flush();

            Optional<Customer> found = customerRepository.findById(customer.getId());
            assertThat(found)
                    .as("repository.findById must see the row EntityManager just persisted in the same tx")
                    .isPresent();
            assertThat(found.get().getName())
                    .as("retrieved entity carries the EM-persisted name")
                    .isEqualTo(name);
            return customer.getId();
        }
    }
}
