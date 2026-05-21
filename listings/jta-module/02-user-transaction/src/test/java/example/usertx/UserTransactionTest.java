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
package example.usertx;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Programmatic UserTransaction. jta-module exposes UserTransaction as
 * a CDI bean wired to the same TransactionManager the @Transactional
 * interceptor drives — so begin/commit/rollback by hand have the same
 * effect on the database as the annotation.
 */
@EnableTestBeans
class UserTransactionTest {

    @Inject
    UserTransaction userTransaction;

    @Inject
    EntityManager entityManager;

    @Test
    void beginCommitPersistsAndIsVisibleToTheNextTransaction() throws Exception {
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
            long count = entityManager
                    .createQuery("SELECT COUNT(m) FROM Marker m", Long.class)
                    .getSingleResult();
            assertThat(count).isEqualTo(1L);
        } finally {
            userTransaction.commit();
        }
    }
}
