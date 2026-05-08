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
package org.os890.jawelte.tests.jpa.scenario13;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** Outer @Transactional service — calls inner via a separate CDI bean so the
 * interceptor fires on both levels (no self-invocation). */
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
     * Persist an outer customer, then call inner which persists its own customer
     * in a nested @Transactional. Both commit.
     *
     * @param outerName the outer customer's name
     * @param innerName the inner customer's name
     */
    @Transactional
    public void outerPersistsAndCallsInner(String outerName, String innerName) {
        entityManager.persist(new Customer(outerName));
        innerService.persistInInnerTx(innerName);
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
}
