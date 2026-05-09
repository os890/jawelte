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
package org.os890.jawelte.tests.jpa.scenario34;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.UserTransaction;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * {@code UserTransaction.rollback()} discards a row that was persisted +
 * flushed before the rollback. A subsequent fresh tx sees an empty table.
 */
@EnableTestBeans
public class Scenario34Test {

    @Inject
    private UserTransaction userTransaction;

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public Scenario34Test() {
    }

    /** persist + flush + rollback → row count is zero. */
    @Test
    public void rollbackDiscardsThePersist() throws Exception {
        userTransaction.begin();
        entityManager.persist(new Marker());
        entityManager.flush();
        userTransaction.rollback();

        userTransaction.begin();
        try {
            long count = entityManager
                    .createQuery("SELECT COUNT(m) FROM Marker m", Long.class)
                    .getSingleResult();
            assertThat(count)
                    .as("UserTransaction.rollback() must discard the row that was persisted before it")
                    .isZero();
        } finally {
            userTransaction.commit();
        }
    }
}
