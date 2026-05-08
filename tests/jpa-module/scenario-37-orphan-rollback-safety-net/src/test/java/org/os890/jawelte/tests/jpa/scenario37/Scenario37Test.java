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
package org.os890.jawelte.tests.jpa.scenario37;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Method 1 calls {@code UserTransaction.begin()} and persists a row, then
 * returns without commit/rollback — leaving the strategy in
 * {@code isActive() == true}. {@code JpaLifecycleAdapter.afterEach}'s
 * orphan-rollback safety net must roll the stray tx back so method 2 sees
 * an empty table and a fresh {@code STATUS_NO_TRANSACTION} state.
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Scenario37Test {

    @Inject
    private UserTransaction userTransaction;

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public Scenario37Test() {
    }

    /** Method 1: open a UT and never commit — leaves the tx active. */
    @Test
    @Order(1)
    public void method1OpensOrphanUserTransaction() throws Exception {
        userTransaction.begin();
        entityManager.persist(new Marker());
        entityManager.flush();
        // intentionally no commit() / rollback() — the safety net must catch this
    }

    /** Method 2: orphan was rolled back; status is fresh and the table is empty. */
    @Test
    @Order(2)
    public void method2SeesCleanStateAfterSafetyNetRollback() throws Exception {
        assertThat(userTransaction.getStatus())
                .as("the orphan UserTransaction from method 1 must have been rolled back "
                        + "by the safety net before method 2 starts")
                .isEqualTo(Status.STATUS_NO_TRANSACTION);

        userTransaction.begin();
        try {
            long count = entityManager
                    .createQuery("SELECT COUNT(m) FROM Marker m", Long.class)
                    .getSingleResult();
            assertThat(count)
                    .as("the persisted row must be discarded by the orphan rollback + per-method cleanup")
                    .isZero();
        } finally {
            userTransaction.commit();
        }
    }
}
