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
package org.os890.jawelte.tests.jpa.scenario67;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * When {@code NativeSqlDeleteDbCleanupStrategy}'s fast path (drop FKs /
 * DELETE rows / re-add FKs) fails part-way, it must NOT leave the
 * database dirty and rethrow. It rolls the failed transaction back and
 * then drops + recreates the mapped schema, which both guarantees an
 * empty database and restores it with named foreign keys.
 *
 * <p>The fast-path failure is forced by
 * {@link TestScenarioFailingTableNameResolver}, which appends a
 * non-existent table to the cleanup list so the {@code DELETE} against
 * it throws on H2. (H2 auto-names every FK, so the anonymous-FK trigger
 * the fallback was written for cannot be reproduced directly there; the
 * missing table drives the same recovery path.)
 *
 * <p>Method 1 persists and commits a row; its per-method cleanup (in
 * {@code afterEach}) hits the missing table, rolls back, and recreates
 * the schema — it must complete without throwing. On the unfixed
 * strategy the advisory failure is rethrown after the rollback, so the
 * row survives and {@code afterEach} fails. Method 2 confirms the
 * recreate fallback produced a clean, queryable table rather than a
 * dirty rollback.
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Scenario67Test {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public Scenario67Test() {
    }

    @Test
    @Order(1)
    @Transactional
    public void cleanupFailureFallsBackToSchemaRecreateWithoutThrowing() {
        entityManager.persist(new Widget("w1"));
        entityManager.flush();

        long widgets = entityManager
                .createQuery("SELECT COUNT(w) FROM Widget w", Long.class)
                .getSingleResult();
        assertThat(widgets)
                .as("the row is committed before per-method cleanup runs")
                .isEqualTo(1L);
        // The load-bearing check is implicit: per-method cleanup runs in
        // afterEach, fails the fast path on the missing table, and must
        // recover via rollback + schema recreate instead of rethrowing.
    }

    @Test
    @Order(2)
    @Transactional
    public void recreateFallbackLeftACleanQueryableTable() {
        long widgets = entityManager
                .createQuery("SELECT COUNT(w) FROM Widget w", Long.class)
                .getSingleResult();
        assertThat(widgets)
                .as("the recreate fallback must have produced an empty table, "
                        + "not a dirty rollback that preserved the previous row")
                .isZero();
    }
}
