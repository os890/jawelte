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
package org.os890.jawelte.tests.jta.scenario02;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** {@code @Transactional} CDI service. */
@ApplicationScoped
public class CustomerService {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public CustomerService() {
    }

    /**
     * Persist a {@link Customer} and return its assigned id.
     *
     * @param name the customer's name
     * @return the persisted id
     */
    @Transactional
    public Long createCustomer(String name) {
        Customer customer = new Customer(name);
        entityManager.persist(customer);
        return customer.getId();
    }

    /**
     * Total customer count.
     *
     * @return the row count
     */
    @Transactional
    public long countCustomers() {
        return entityManager
                .createQuery("SELECT COUNT(c) FROM Customer c", Long.class)
                .getSingleResult();
    }
}
