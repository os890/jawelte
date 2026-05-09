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
package org.os890.jawelte.tests.jpa.scenario11;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** {@code @Transactional} service that persists then throws a checked exception. */
@ApplicationScoped
public class CustomerService {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public CustomerService() {
    }

    /**
     * Persist a customer and immediately throw a checked {@link BusinessException}.
     * jawelte's project-wide rollback rule rolls back even on checked exceptions
     * (intentional divergence from the Jakarta EE default).
     *
     * @param name the customer's name
     * @throws BusinessException always
     */
    @Transactional
    public void persistAndThrowChecked(String name) throws BusinessException {
        entityManager.persist(new Customer(name));
        entityManager.flush();
        throw new BusinessException("scenario-11: forced checked exception");
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
