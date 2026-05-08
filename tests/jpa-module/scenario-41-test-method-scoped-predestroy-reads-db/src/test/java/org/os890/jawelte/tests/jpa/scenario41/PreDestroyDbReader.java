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
package org.os890.jawelte.tests.jpa.scenario41;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.os890.jawelte.module.scope.api.TestMethodScoped;

/**
 * {@code @TestMethodScoped} bean whose {@code @PreDestroy} runs a JPQL query
 * via a fresh {@link EntityManager} taken from the JVM-cached
 * {@link EntityManagerFactory}. The result is captured into a static
 * {@link AtomicReference} so the test method that follows can assert on it.
 *
 * <p>The contract under test: jpa-module's {@code JpaLifecycleAdapter} keeps
 * the EMF open across the afterEach lifecycle, so a {@code @PreDestroy}
 * triggered by scope-module's afterEach can still hit the DB. The per-method
 * cleanup ran (table got truncated), so the count is zero.
 */
@TestMethodScoped
public class PreDestroyDbReader {

    /** Holds the row count observed by {@code @PreDestroy}, or {@code null} if PreDestroy never ran. */
    public static final AtomicReference<Long> COUNT_AT_PREDESTROY = new AtomicReference<>();

    /** Captures any exception the {@code @PreDestroy} JPQL query raised. */
    public static final AtomicReference<Throwable> FAILURE_AT_PREDESTROY = new AtomicReference<>();

    @Inject
    private EntityManagerFactory entityManagerFactory;

    /** No-arg constructor required by CDI. */
    public PreDestroyDbReader() {
    }

    /** Reset both result holders to {@code null}. */
    public static void reset() {
        COUNT_AT_PREDESTROY.set(null);
        FAILURE_AT_PREDESTROY.set(null);
    }

    /** Force the contextual proxy to materialise its bean instance. */
    public void touch() {
    }

    /** Read the row count via a fresh EntityManager + standalone tx. */
    @PreDestroy
    void onPreDestroy() {
        try {
            EntityManager freshEntityManager = entityManagerFactory.createEntityManager();
            try {
                freshEntityManager.getTransaction().begin();
                try {
                    Long count = freshEntityManager
                            .createQuery("SELECT COUNT(m) FROM Marker m", Long.class)
                            .getSingleResult();
                    COUNT_AT_PREDESTROY.set(count);
                } finally {
                    freshEntityManager.getTransaction().rollback();
                }
            } finally {
                freshEntityManager.close();
            }
        } catch (Throwable failure) {
            FAILURE_AT_PREDESTROY.set(failure);
        }
    }
}
