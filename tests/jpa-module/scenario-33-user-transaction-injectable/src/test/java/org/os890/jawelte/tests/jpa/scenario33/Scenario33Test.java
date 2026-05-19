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
package org.os890.jawelte.tests.jpa.scenario33;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;


import io.quarkus.test.junit.QuarkusTest;
/**
 * {@code UserTransaction} is injectable; in resting state it reports
 * {@link Status#STATUS_NO_TRANSACTION}; {@code begin()} + persist +
 * {@code commit()} produces a row that is visible from a fresh tx.
 */
@EnableTestBeans
@QuarkusTest
public class Scenario33Test {

    @Inject
    private UserTransaction userTransaction;

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public Scenario33Test() {
    }

    /** Inject + begin/persist/commit + verify row visible. */
    @Test
    public void injectableUserTransactionRunsCommit() throws Exception {
        assertThat(userTransaction)
                .as("@Inject UserTransaction must resolve to a non-null bean")
                .isNotNull();
        assertThat(userTransaction.getStatus())
                .as("freshly looked-up UserTransaction must report STATUS_NO_TRANSACTION")
                .isEqualTo(Status.STATUS_NO_TRANSACTION);

        userTransaction.begin();
        try {
            entityManager.persist(new Marker());
            entityManager.flush();
        } finally {
            userTransaction.commit();
        }

        userTransaction.begin();
        try {
            long count = entityManager
                    .createQuery("SELECT COUNT(m) FROM Marker m", Long.class)
                    .getSingleResult();
            assertThat(count)
                    .as("the row persisted under UserTransaction must be visible from a fresh tx")
                    .isEqualTo(1L);
        } finally {
            userTransaction.commit();
        }
    }
}
