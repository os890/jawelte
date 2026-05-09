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
package org.os890.jawelte.tests.jpa.scenario57;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.transaction.Transactional;

/**
 * Two persist paths to a real {@link Marker} entity:
 *
 * <ul>
 *   <li>{@link #frameworkDriven()} — runs through {@code @Transactional},
 *       so jpa-module's {@code TransactionStrategy} drives begin /
 *       commit and fires the matching CDI events.</li>
 *   <li>{@link #userDriven()} — uses the injected
 *       {@link EntityManagerFactory} directly to open a fresh
 *       {@link EntityManager}, drive its
 *       {@link EntityTransaction}, and close it. The strategy is
 *       never called, so no events fire.</li>
 * </ul>
 */
@ApplicationScoped
public class MarkerService {

    @Inject
    private EntityManager entityManager;

    @Inject
    private EntityManagerFactory entityManagerFactory;

    /** Default constructor required by CDI. */
    public MarkerService() {
    }

    /** Persist a marker through the framework's @Transactional path. */
    @Transactional
    public void frameworkDriven() {
        entityManager.persist(new Marker("framework"));
    }

    /**
     * Persist a marker outside the framework's transaction
     * machinery: open a brand-new {@code EntityManager} from the
     * injected {@code EntityManagerFactory}, drive its
     * {@code EntityTransaction} directly, and close it. Bypasses
     * the strategy entirely.
     */
    public void userDriven() {
        EntityManager direct = entityManagerFactory.createEntityManager();
        EntityTransaction transaction = direct.getTransaction();
        transaction.begin();
        try {
            direct.persist(new Marker("user"));
            direct.flush();
            transaction.commit();
        } catch (RuntimeException failure) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw failure;
        } finally {
            direct.close();
        }
    }
}
