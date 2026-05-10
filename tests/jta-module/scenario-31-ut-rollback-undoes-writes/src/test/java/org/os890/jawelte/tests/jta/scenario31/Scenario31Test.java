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
package org.os890.jawelte.tests.jta.scenario31;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.UserTransaction;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Port of jpa-module scenario 34 — programmatic
 * {@link UserTransaction#rollback()} undoes pending writes under JTA.
 * Begin a JTA tx, persist, roll back, then verify the row is not
 * visible from a fresh tx.
 */
@EnableTestBeans
public class Scenario31Test {

    @Inject
    private UserTransaction userTransaction;

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public Scenario31Test() {
    }

    @Test
    public void rollbackUndoesPendingPersist() throws Exception {
        userTransaction.begin();
        try {
            entityManager.persist(new Marker());
        } finally {
            userTransaction.rollback();
        }

        userTransaction.begin();
        try {
            long count = entityManager.createQuery("SELECT COUNT(m) FROM Marker m", Long.class)
                    .getSingleResult();
            assertThat(count)
                    .as("rolled-back JTA tx must leave the table empty")
                    .isZero();
        } finally {
            userTransaction.commit();
        }
    }
}
