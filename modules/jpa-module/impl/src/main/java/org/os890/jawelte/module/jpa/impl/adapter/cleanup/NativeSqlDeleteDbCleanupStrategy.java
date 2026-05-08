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
package org.os890.jawelte.module.jpa.impl.adapter.cleanup;

import java.util.List;

import jakarta.annotation.Priority;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.port.DbCleanupStrategy;
import org.os890.jawelte.module.jpa.api.port.TableNameResolver;

/**
 * Fallback {@link DbCleanupStrategy}: issues native-SQL
 * {@code DELETE FROM "<table>"} for every table returned by the active
 * {@link TableNameResolver}, in <strong>reverse</strong> iteration
 * order so child rows go before their parents in the common acyclic-FK
 * case. Per-table failures aggregate per the project exception policy
 * (TICKET-001): the first failure becomes the primary, subsequent
 * failures (and any rollback failure) attach via
 * {@link Throwable#addSuppressed(Throwable)}.
 *
 * <p>{@code @Priority(Integer.MAX_VALUE)} — absolute fallback. The
 * H2-targeted {@link JdbcTruncateDbCleanupStrategy} sits one priority
 * rank ahead and wins by default; consumers running against a non-H2
 * database that lacks {@code TRUNCATE} or {@code SET REFERENTIAL_INTEGRITY}
 * drop the JdbcTruncate jar from the test classpath and let this
 * native-DELETE fallback take over.
 *
 * <p>Native SQL (rather than JPQL) so the strategy can target
 * <em>any</em> table — including {@code @JoinTable},
 * {@code @ElementCollection}, sequence, and trigger-populated tables
 * that have no JPA {@code @Entity} mapping.
 */
@Priority(Integer.MAX_VALUE)
public class NativeSqlDeleteDbCleanupStrategy implements DbCleanupStrategy {

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public NativeSqlDeleteDbCleanupStrategy() {
    }

    @Override
    public void cleanAllTables(String persistenceUnitName, EntityManagerFactory entityManagerFactory) {
        TableNameResolver tableNameResolver = TestContext.loadService(TableNameResolver.class);
        List<String> tableNames = tableNameResolver.resolveTableNames(persistenceUnitName, entityManagerFactory);
        if (tableNames.isEmpty()) {
            return;
        }
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        RuntimeException primary = null;
        try {
            entityManager.getTransaction().begin();
            for (int index = tableNames.size() - 1; index >= 0; index--) {
                String tableName = tableNames.get(index);
                try {
                    entityManager.createNativeQuery("DELETE FROM \"" + tableName + "\"").executeUpdate();
                } catch (RuntimeException perTable) {
                    if (primary == null) {
                        primary = new RuntimeException(
                                "Cleanup failed for table '" + tableName + "' of persistence unit '"
                                        + persistenceUnitName + "'", perTable);
                    } else {
                        primary.addSuppressed(perTable);
                    }
                }
            }
            if (primary == null) {
                entityManager.getTransaction().commit();
            } else {
                try {
                    entityManager.getTransaction().rollback();
                } catch (RuntimeException rollbackFailure) {
                    primary.addSuppressed(rollbackFailure);
                }
                throw primary;
            }
        } finally {
            try {
                entityManager.close();
            } catch (RuntimeException closeFailure) {
                if (primary != null) {
                    primary.addSuppressed(closeFailure);
                }
            }
        }
    }
}
