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
package org.os890.jawelte.tests.springdata.scenario11;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.mockito.internal.creation.bytebuddy.MockAccess;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Scenario 11 — `@EnableTestBeans(limitToTestBeans=true)` disables
 * cdi-module's auto-mocker entirely. The repository still resolves
 * because the extension's synthetic bean is registered regardless
 * of the auto-mocker's state.
 */
@EnableTestBeans(limitToTestBeans = true)
public class Scenario11Test {

    @Inject
    private CrudInvoker crudInvoker;

    /** No-arg constructor for CDI. */
    public Scenario11Test() {
    }

    /** Repository works in `limitToTestBeans=true` mode; it is the real Spring Data impl. */
    @Test
    public void repositoryWorksWithLimitToTestBeans() {
        Long savedId = crudInvoker.save("Alice");
        assertThat(savedId)
                .as("repository.save resolved through the real Spring Data implementation")
                .isNotNull();
        assertThat(crudInvoker.getRepository())
                .as("the injected repository is not a Mockito mock — limitToTestBeans disabled auto-mocking")
                .isNotInstanceOf(MockAccess.class);
    }

    /** Transactional bridge through which the test invokes the repository. */
    @ApplicationScoped
    public static class CrudInvoker {

        @Inject
        private CustomerRepository customerRepository;

        /** No-arg constructor for CDI. */
        public CrudInvoker() {
        }

        /**
         * Save a customer.
         *
         * @param name the customer's name
         * @return the persisted id
         */
        @Transactional
        public Long save(String name) {
            return customerRepository.save(new Customer(name)).getId();
        }

        /**
         * Expose the repository for mock-instance inspection.
         *
         * @return the auto-discovered repository
         */
        public CustomerRepository getRepository() {
            return customerRepository;
        }
    }
}
