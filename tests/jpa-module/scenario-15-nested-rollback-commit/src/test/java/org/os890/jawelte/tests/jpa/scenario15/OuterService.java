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
package org.os890.jawelte.tests.jpa.scenario15;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** Outer @Transactional service: persists, calls inner (which rolls back), catches, commits. */
@ApplicationScoped
public class OuterService {

    @Inject
    private EntityManager entityManager;

    @Inject
    private InnerService innerService;

    /** No-arg constructor for CDI. */
    public OuterService() {
    }

    /**
     * Persist an outer customer, invoke inner (which throws), catch the
     * exception so the outer tx survives, then return so outer commits.
     * Inner's row was rolled back by its own interceptor; outer's row stays.
     *
     * @param outerName the outer customer's name
     * @param innerName the inner customer's name
     */
    @Transactional
    public void outerPersistsCatchesInnerRollback(String outerName, String innerName) {
        entityManager.persist(new Customer(outerName));
        try {
            innerService.persistAndThrow(innerName);
        } catch (RuntimeException ignored) {
            // intentionally swallowed — outer continues + commits
        }
    }

    /**
     * Total customer count from a fresh tx.
     *
     * @return the row count
     */
    @Transactional
    public long countCustomers() {
        return entityManager
                .createQuery("SELECT COUNT(c) FROM Customer c", Long.class)
                .getSingleResult();
    }

    /**
     * The single surviving customer's name (or {@code null} if the table is empty).
     *
     * @return the name of the remaining customer, or {@code null}
     */
    @Transactional
    public String singleSurvivorName() {
        return entityManager
                .createQuery("SELECT c.name FROM Customer c", String.class)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }
}
