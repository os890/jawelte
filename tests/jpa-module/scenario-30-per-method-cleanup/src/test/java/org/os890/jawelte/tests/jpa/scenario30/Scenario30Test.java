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
package org.os890.jawelte.tests.jpa.scenario30;

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
 * Per-method cleanup contract: method 1 persists a row inside its own
 * {@code @Transactional} tx; method 2 starts on an empty table because
 * jpa-module's {@code JpaLifecycleAdapter.afterEach} ran the active
 * {@code DbCleanupStrategy} between the two methods. Mirrors POC's
 * {@code JpaTestExtensionTest.perMethodCleanupWorks}.
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Scenario30Test {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public Scenario30Test() {
    }

    /** Method 1: persist a marker, then verify the row count is 1 within this tx. */
    @Test
    @Order(1)
    @Transactional
    public void firstMethodPersists() {
        entityManager.persist(new Marker());
        entityManager.flush();

        long count = entityManager
                .createQuery("SELECT COUNT(m) FROM Marker m", Long.class)
                .getSingleResult();
        assertThat(count)
                .as("the @Transactional method must see the row it just persisted + flushed")
                .isEqualTo(1L);
    }

    /** Method 2: per-method cleanup ran in afterEach → table is empty again. */
    @Test
    @Order(2)
    @Transactional
    public void secondMethodSeesEmptyTable() {
        long count = entityManager
                .createQuery("SELECT COUNT(m) FROM Marker m", Long.class)
                .getSingleResult();
        assertThat(count)
                .as("per-method cleanup must wipe the row method 1 persisted")
                .isZero();
    }
}
