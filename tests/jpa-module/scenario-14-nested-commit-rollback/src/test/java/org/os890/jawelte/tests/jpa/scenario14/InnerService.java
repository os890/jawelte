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
package org.os890.jawelte.tests.jpa.scenario14;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** Inner @Transactional service that commits successfully. */
@ApplicationScoped
public class InnerService {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public InnerService() {
    }

    /**
     * Persist a customer in a fresh inner-level tx and commit.
     *
     * @param name the customer's name
     */
    @Transactional
    public void persistInInnerTx(String name) {
        entityManager.persist(new Customer(name));
    }
}
