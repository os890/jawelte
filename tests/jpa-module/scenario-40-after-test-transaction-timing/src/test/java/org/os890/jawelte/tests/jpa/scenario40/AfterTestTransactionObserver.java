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
package org.os890.jawelte.tests.jpa.scenario40;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.os890.jawelte.core.api.event.AfterTestTransaction;

/**
 * Observes {@link AfterTestTransaction} and queries the DB at fire time
 * via a fresh {@link EntityManager} created from the JVM-cached EMF —
 * captures the row count so the next test method can verify the timing
 * contract: the observer fires AFTER the test's @Transactional commit
 * but BEFORE jpa-module's per-method cleanup, so the persisted row is
 * still in the DB when the observer runs.
 */
@ApplicationScoped
public class AfterTestTransactionObserver {

    /** Row count observed at AfterTestTransaction fire time, or {@code null} until set. */
    public static final AtomicReference<Long> COUNT_AT_FIRE = new AtomicReference<>();

    @Inject
    private EntityManagerFactory entityManagerFactory;

    /** Default constructor required by CDI. */
    public AfterTestTransactionObserver() {
    }

    /** Reset the recorded count. */
    public static void reset() {
        COUNT_AT_FIRE.set(null);
    }

    /** Sample the row count when AfterTestTransaction fires. */
    public void onFire(@Observes AfterTestTransaction event) {
        EntityManager freshEntityManager = entityManagerFactory.createEntityManager();
        try {
            freshEntityManager.getTransaction().begin();
            try {
                Long count = freshEntityManager
                        .createQuery("SELECT COUNT(m) FROM Marker m", Long.class)
                        .getSingleResult();
                COUNT_AT_FIRE.set(count);
            } finally {
                freshEntityManager.getTransaction().rollback();
            }
        } finally {
            freshEntityManager.close();
        }
    }
}
