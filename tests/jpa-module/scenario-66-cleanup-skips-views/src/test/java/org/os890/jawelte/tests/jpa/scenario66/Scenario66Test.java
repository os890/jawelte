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
package org.os890.jawelte.tests.jpa.scenario66;

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
 * A SQL view in the {@code PUBLIC} schema must NOT break per-method
 * cleanup. {@code INFORMATION_SCHEMA.TABLES} lists {@code VIEW} rows
 * alongside {@code BASE TABLE} rows, and the default cleanup strategy
 * issues {@code TRUNCATE}/{@code DELETE} per resolved name — which H2
 * rejects on a view, aborting cleanup. The default
 * {@code TableNameResolver} therefore filters
 * {@code TABLE_TYPE = 'BASE TABLE'}.
 *
 * <p>Method 1 persists a row and creates a view over the base table;
 * its per-method cleanup (in {@code afterEach}) must complete without
 * error (on the unfixed resolver it throws while truncating the view).
 * Method 2 confirms cleanup actually truncated the base table and left
 * the view intact.
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Scenario66Test {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public Scenario66Test() {
    }

    @Test
    @Order(1)
    @Transactional
    public void viewPresentDoesNotBreakPerMethodCleanup() {
        entityManager.persist(new Widget("w1"));
        entityManager.flush();

        entityManager.createNativeQuery(
                "CREATE VIEW IF NOT EXISTS widget_view AS SELECT id, name FROM widget")
                .executeUpdate();

        Number throughView = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM widget_view")
                .getSingleResult();
        assertThat(throughView.longValue())
                .as("the view reflects the persisted row")
                .isEqualTo(1L);
        // The load-bearing check is implicit: per-method cleanup runs in
        // afterEach and must NOT fail trying to TRUNCATE widget_view.
    }

    @Test
    @Order(2)
    @Transactional
    public void cleanupTruncatedTheBaseTableAndLeftTheViewIntact() {
        long widgets = entityManager
                .createQuery("SELECT COUNT(w) FROM Widget w", Long.class)
                .getSingleResult();
        assertThat(widgets)
                .as("per-method cleanup must truncate the base table")
                .isZero();

        Number throughView = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM widget_view")
                .getSingleResult();
        assertThat(throughView.longValue())
                .as("the view still exists after cleanup (excluded from TRUNCATE), "
                        + "now reflecting the emptied table")
                .isZero();
    }
}
