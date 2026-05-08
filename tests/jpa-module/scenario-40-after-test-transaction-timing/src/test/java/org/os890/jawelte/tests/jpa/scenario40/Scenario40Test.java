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
 * The {@code AfterTestTransaction} CDI event fires in
 * {@code JpaLifecycleAdapter.afterEach} <em>after</em> the test's
 * {@code @Transactional} commits and <em>before</em> the per-method DB
 * cleanup runs — so an observer that queries the DB at fire time still
 * sees the test's persisted rows.
 *
 * <p>Method 1 persists one row inside its own {@code @Transactional}.
 * Method 2 (also {@code @Transactional}) inspects the static counter the
 * observer set in afterEach between the two methods — the count must be 1.
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Scenario40Test {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public Scenario40Test() {
    }

    /** Method 1: persist a row; the AfterTestTransaction observer fires in afterEach after this. */
    @Test
    @Order(1)
    @Transactional
    public void method1PersistsOneRow() {
        AfterTestTransactionObserver.reset();
        entityManager.persist(new Marker());
        entityManager.flush();
    }

    /** Method 2: the observer ran between the two methods and saw the row pre-cleanup. */
    @Test
    @Order(2)
    @Transactional
    public void method2ObserverSawRowBeforeCleanup() {
        assertThat(AfterTestTransactionObserver.COUNT_AT_FIRE.get())
                .as("AfterTestTransaction fired between method 1's commit and the per-method "
                        + "cleanup; the observer's JPQL count must therefore see method 1's "
                        + "committed row (== 1)")
                .isEqualTo(1L);

        long currentCount = entityManager
                .createQuery("SELECT COUNT(m) FROM Marker m", Long.class)
                .getSingleResult();
        assertThat(currentCount)
                .as("by the time method 2 runs, per-method cleanup has wiped the table")
                .isZero();
    }
}
