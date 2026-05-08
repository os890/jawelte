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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.Priority;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.hibernate.Session;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.port.DbCleanupStrategy;
import org.os890.jawelte.module.jpa.api.port.TableNameResolver;

/**
 * Fallback {@link DbCleanupStrategy}: two-pass cleanup using portable
 * SQL only. <em>Pass 1</em> walks {@link java.sql.DatabaseMetaData#getImportedKeys}
 * for every table returned by the active {@link TableNameResolver} and
 * issues {@code UPDATE "<table>" SET "<fkCol>" = NULL} for each
 * <em>nullable</em> foreign-key column — breaking circular references
 * (self-referencing parent/child shapes, two-table cycles) without
 * relying on database-specific {@code SET REFERENTIAL_INTEGRITY} or
 * {@code SET FOREIGN_KEY_CHECKS} primitives. <em>Pass 2</em> issues
 * {@code DELETE FROM "<table>"} in <strong>reverse</strong> table-list
 * order so child rows go before their parents in the common acyclic-FK
 * case.
 *
 * <p>Per-table failures aggregate per the project exception policy
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
 *
 * <p><strong>Circular-FK scope.</strong> The Pass 1 null-update
 * handles every cycle in which at least one FK column on each cycle
 * edge is nullable — which is the JPA default for {@code @ManyToOne}
 * and the typical shape for self-referencing parent/child hierarchies
 * (punch-list §2.3). Cycles where every FK column is {@code NOT NULL}
 * still cannot be resolved without database-specific RI-disable
 * primitives — those consumers should keep
 * {@link JdbcTruncateDbCleanupStrategy} on the classpath (its
 * {@code SET REFERENTIAL_INTEGRITY FALSE} approach handles NOT NULL
 * cycles on H2) or ship a vendor-specific custom strategy.
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
        AtomicReference<RuntimeException> primary = new AtomicReference<>();
        try {
            entityManager.getTransaction().begin();
            Session session = entityManager.unwrap(Session.class);
            session.doWork(connection -> {
                try (Statement statement = connection.createStatement()) {
                    // Pass 1: null out every nullable FK column so circular references
                    // (self-FK or two-table cycles) don't block Pass 2's deletes.
                    for (String tableName : tableNames) {
                        nullNullableForeignKeys(connection, statement, tableName, persistenceUnitName, primary);
                    }
                    // Pass 2: delete in reverse table-list order.
                    for (int index = tableNames.size() - 1; index >= 0; index--) {
                        String tableName = tableNames.get(index);
                        try {
                            statement.execute("DELETE FROM \"" + tableName + "\"");
                        } catch (SQLException perTable) {
                            aggregateFailure(
                                    primary,
                                    "Cleanup failed for table '" + tableName + "' of persistence unit '"
                                            + persistenceUnitName + "'",
                                    perTable);
                        }
                    }
                }
            });
            if (primary.get() == null) {
                entityManager.getTransaction().commit();
            } else {
                try {
                    entityManager.getTransaction().rollback();
                } catch (RuntimeException rollbackFailure) {
                    primary.get().addSuppressed(rollbackFailure);
                }
                throw primary.get();
            }
        } finally {
            try {
                entityManager.close();
            } catch (RuntimeException closeFailure) {
                if (primary.get() != null) {
                    primary.get().addSuppressed(closeFailure);
                }
            }
        }
    }

    /**
     * Pass-1 helper: for every imported key on {@code tableName} whose
     * FK column is nullable, run {@code UPDATE "<table>" SET "<fkCol>" = NULL}.
     * Aggregates per-column failures (e.g. metadata says nullable but a
     * deferrable constraint refuses the update) so Pass 2 still gets a
     * chance to attempt the delete.
     */
    private static void nullNullableForeignKeys(
            Connection connection,
            Statement statement,
            String tableName,
            String persistenceUnitName,
            AtomicReference<RuntimeException> primary) throws SQLException {
        try (ResultSet importedKeys = connection.getMetaData().getImportedKeys(null, null, tableName)) {
            while (importedKeys.next()) {
                String fkColumn = importedKeys.getString("FKCOLUMN_NAME");
                if (!isColumnNullable(connection, tableName, fkColumn)) {
                    continue;
                }
                try {
                    statement.executeUpdate("UPDATE \"" + tableName + "\" SET \"" + fkColumn + "\" = NULL");
                } catch (SQLException nullFailure) {
                    aggregateFailure(
                            primary,
                            "Failed to null FK column '" + fkColumn + "' on table '" + tableName
                                    + "' of persistence unit '" + persistenceUnitName + "'",
                            nullFailure);
                }
            }
        }
    }

    /**
     * @return {@code true} when JDBC metadata reports the column as
     *     {@code IS_NULLABLE = 'YES'}; {@code false} for NOT NULL or
     *     when the column doesn't surface in metadata at all.
     */
    private static boolean isColumnNullable(Connection connection, String tableName, String columnName)
            throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            if (columns.next()) {
                return "YES".equalsIgnoreCase(columns.getString("IS_NULLABLE"));
            }
        }
        return false;
    }

    private static void aggregateFailure(
            AtomicReference<RuntimeException> primary, String message, SQLException cause) {
        RuntimeException current = primary.get();
        RuntimeException wrapped = new RuntimeException(message, cause);
        if (current == null) {
            primary.set(wrapped);
        } else {
            current.addSuppressed(wrapped);
        }
    }
}
