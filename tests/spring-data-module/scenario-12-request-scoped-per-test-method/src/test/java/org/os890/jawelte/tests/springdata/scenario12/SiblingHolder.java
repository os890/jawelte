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
package org.os890.jawelte.tests.springdata.scenario12;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Application-scoped sibling bean whose injection of
 * {@code CustomerRepository} resolves — through the CDI client
 * proxy — to the same {@code @RequestScoped} instance that the
 * test class holds, for the duration of one test method.
 */
@ApplicationScoped
public class SiblingHolder {

    @Inject
    private CustomerRepository customerRepository;

    /** No-arg constructor for CDI. */
    public SiblingHolder() {
    }

    /**
     * Get the CDI client proxy reference for the repository
     * injected into this bean.
     *
     * @return the auto-discovered, request-scoped repository proxy
     */
    public CustomerRepository getCustomerRepository() {
        return customerRepository;
    }

    /**
     * Save a customer through the shared request-scoped repository.
     *
     * @param name the customer's name
     * @return the assigned id
     */
    @Transactional
    public Long saveCustomer(String name) {
        return customerRepository.save(new Customer(name)).getId();
    }

    /**
     * Count rows via the shared request-scoped repository.
     *
     * @return the customer count
     */
    @Transactional
    public long customerCount() {
        return customerRepository.count();
    }
}
