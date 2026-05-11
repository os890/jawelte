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
package org.os890.jawelte.tests.jta.scenario21;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Ticket-006 scenario #21 — programmatic
 * {@link UserTransaction#begin()} + {@link UserTransaction#commit()}
 * drives the same {@code TransactionManager} the strategy uses.
 * Persisting an entity inside a programmatic JTA boundary commits to
 * the database; the row is visible to a subsequent transaction.
 */
@EnableTestBeans
public class Scenario21Test {

    @Inject
    private UserTransaction userTransaction;

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public Scenario21Test() {
    }

    @Test
    public void programmaticUtBeginCommitPersists() throws Exception {
        assertThat(userTransaction.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);

        userTransaction.begin();
        try {
            assertThat(userTransaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
            entityManager.persist(new Marker());
            userTransaction.commit();
        } catch (RuntimeException unexpected) {
            if (userTransaction.getStatus() != Status.STATUS_NO_TRANSACTION) {
                userTransaction.rollback();
            }
            throw unexpected;
        }

        userTransaction.begin();
        try {
            long count = entityManager.createQuery("SELECT COUNT(m) FROM Marker m", Long.class)
                    .getSingleResult();
            assertThat(count)
                    .as("a fresh JTA tx must see the row committed by the previous programmatic UT.commit()")
                    .isEqualTo(1L);
        } finally {
            userTransaction.commit();
        }
    }
}
