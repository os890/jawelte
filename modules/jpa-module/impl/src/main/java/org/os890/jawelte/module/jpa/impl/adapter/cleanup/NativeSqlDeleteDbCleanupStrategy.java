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
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.Priority;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.hibernate.Session;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.port.DbCleanupStrategy;
import org.os890.jawelte.module.jpa.api.port.TableNameResolver;

/**
 * Fallback {@link DbCleanupStrategy}: drops every foreign-key
 * constraint, deletes all rows, then re-adds the constraints. The
 * drop / re-add pair is metadata-level — no row scans, no
 * row-by-row constraint checks during the DELETEs — so cleanup
 * cost stays bounded by table count even when the test seeded
 * thousands of rows. Resolves circular FKs (single-table self-FK
 * AND multi-table cycles) without relying on database-specific
 * primitives like {@code SET REFERENTIAL_INTEGRITY} (H2) or
 * {@code SET FOREIGN_KEY_CHECKS} (MySQL).
 *
 * <p><strong>Flow</strong> per {@link #cleanAllTables}:
 * <ol>
 *   <li>Walk {@link DatabaseMetaData#getImportedKeys} for every
 *       table returned by the active {@link TableNameResolver};
 *       capture each FK constraint's full definition (name, FK
 *       columns, referenced table + columns, ON DELETE / ON UPDATE
 *       rules) so it can be re-emitted verbatim.</li>
 *   <li>Issue {@code ALTER TABLE "<t>" DROP CONSTRAINT "<fk>"}
 *       for every captured FK.</li>
 *   <li>Issue {@code DELETE FROM "<t>"} for every table in
 *       reverse table-list order. With the FKs gone, the deletes
 *       run unconstrained and are independent of declaration
 *       order.</li>
 *   <li>Re-add every captured FK via
 *       {@code ALTER TABLE "<t>" ADD CONSTRAINT "<fk>" FOREIGN KEY (...)
 *       REFERENCES "<r>" (...) ON DELETE <rule> ON UPDATE <rule>}.
 *       Wrapped in a {@code finally} so the schema gets restored
 *       even if step 3 throws — important on databases where DDL
 *       implicitly commits (e.g. MySQL) and the surrounding
 *       transaction's rollback would NOT undo the drops.</li>
 * </ol>
 *
 * <p>Per-step failures aggregate per the project exception policy
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
 * <p><strong>Portability.</strong> The {@code ALTER TABLE … DROP
 * CONSTRAINT} / {@code ADD CONSTRAINT} syntax used here is standard
 * SQL — verified against H2, PostgreSQL, and Oracle. MySQL/MariaDB
 * use the non-standard {@code DROP FOREIGN KEY} keyword instead;
 * consumers running tests against MySQL ship a vendor-specific
 * {@code DbCleanupStrategy} at a lower {@code @Priority}.
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
                List<ForeignKeyDefinition> capturedForeignKeys =
                        captureForeignKeys(connection, tableNames, persistenceUnitName, primary);
                try (Statement statement = connection.createStatement()) {
                    dropForeignKeys(statement, capturedForeignKeys, persistenceUnitName, primary);
                    try {
                        deleteAllRows(statement, tableNames, persistenceUnitName, primary);
                    } finally {
                        // Re-add FKs even if the DELETE phase threw, so the schema is
                        // restored on databases where DDL implicitly commits.
                        addForeignKeys(statement, capturedForeignKeys, persistenceUnitName, primary);
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

    private static List<ForeignKeyDefinition> captureForeignKeys(
            Connection connection,
            List<String> tableNames,
            String persistenceUnitName,
            AtomicReference<RuntimeException> primary) throws SQLException {
        // FKs that span multiple columns surface as multiple metadata rows
        // sharing the same FK_NAME — group by name to collapse them into one
        // ADD CONSTRAINT statement with the column lists in KEY_SEQ order.
        Map<String, ForeignKeyBuilder> byName = new LinkedHashMap<>();
        DatabaseMetaData metaData = connection.getMetaData();
        for (String tableName : tableNames) {
            try (ResultSet importedKeys = metaData.getImportedKeys(null, null, tableName)) {
                while (importedKeys.next()) {
                    String fkName = importedKeys.getString("FK_NAME");
                    if (fkName == null) {
                        // Anonymous FK — can't drop by name; record + skip.
                        aggregateFailure(
                                primary,
                                "Skipping anonymous foreign key on table '" + tableName
                                        + "' of persistence unit '" + persistenceUnitName
                                        + "' — drop-and-readd requires a named constraint",
                                null);
                        continue;
                    }
                    ForeignKeyBuilder builder = byName.computeIfAbsent(
                            fkName, key -> new ForeignKeyBuilder(tableName, key));
                    builder.addColumn(
                            importedKeys.getShort("KEY_SEQ"),
                            importedKeys.getString("FKCOLUMN_NAME"),
                            importedKeys.getString("PKTABLE_NAME"),
                            importedKeys.getString("PKCOLUMN_NAME"),
                            importedKeys.getInt("DELETE_RULE"),
                            importedKeys.getInt("UPDATE_RULE"));
                }
            }
        }
        List<ForeignKeyDefinition> definitions = new ArrayList<>(byName.size());
        for (ForeignKeyBuilder builder : byName.values()) {
            definitions.add(builder.build());
        }
        return definitions;
    }

    private static void dropForeignKeys(
            Statement statement,
            List<ForeignKeyDefinition> foreignKeys,
            String persistenceUnitName,
            AtomicReference<RuntimeException> primary) {
        for (ForeignKeyDefinition fk : foreignKeys) {
            try {
                statement.execute(fk.toDropSql());
            } catch (SQLException dropFailure) {
                aggregateFailure(
                        primary,
                        "Failed to drop foreign key '" + fk.constraintName() + "' on table '"
                                + fk.tableName() + "' of persistence unit '" + persistenceUnitName + "'",
                        dropFailure);
            }
        }
    }

    private static void deleteAllRows(
            Statement statement,
            List<String> tableNames,
            String persistenceUnitName,
            AtomicReference<RuntimeException> primary) {
        for (int index = tableNames.size() - 1; index >= 0; index--) {
            String tableName = tableNames.get(index);
            try {
                statement.execute("DELETE FROM \"" + tableName + "\"");
            } catch (SQLException deleteFailure) {
                aggregateFailure(
                        primary,
                        "Cleanup failed for table '" + tableName + "' of persistence unit '"
                                + persistenceUnitName + "'",
                        deleteFailure);
            }
        }
    }

    private static void addForeignKeys(
            Statement statement,
            List<ForeignKeyDefinition> foreignKeys,
            String persistenceUnitName,
            AtomicReference<RuntimeException> primary) {
        for (ForeignKeyDefinition fk : foreignKeys) {
            try {
                statement.execute(fk.toAddSql());
            } catch (SQLException addFailure) {
                aggregateFailure(
                        primary,
                        "Failed to re-add foreign key '" + fk.constraintName() + "' on table '"
                                + fk.tableName() + "' of persistence unit '" + persistenceUnitName
                                + "' — schema may be inconsistent for subsequent tests",
                        addFailure);
            }
        }
    }

    private static void aggregateFailure(
            AtomicReference<RuntimeException> primary, String message, SQLException cause) {
        RuntimeException current = primary.get();
        RuntimeException wrapped = cause == null
                ? new RuntimeException(message)
                : new RuntimeException(message, cause);
        if (current == null) {
            primary.set(wrapped);
        } else {
            current.addSuppressed(wrapped);
        }
    }

    /** Captured FK definition — enough state to re-emit a verbatim ADD CONSTRAINT. */
    private record ForeignKeyDefinition(
            String tableName,
            String constraintName,
            List<String> fkColumns,
            String referencedTable,
            List<String> referencedColumns,
            int deleteRule,
            int updateRule) {

        String toDropSql() {
            return "ALTER TABLE \"" + tableName + "\" DROP CONSTRAINT \"" + constraintName + "\"";
        }

        String toAddSql() {
            return "ALTER TABLE \"" + tableName + "\" ADD CONSTRAINT \"" + constraintName + "\" "
                    + "FOREIGN KEY (" + quoteAndJoin(fkColumns) + ") "
                    + "REFERENCES \"" + referencedTable + "\" (" + quoteAndJoin(referencedColumns) + ") "
                    + "ON DELETE " + ruleToSql(deleteRule) + " "
                    + "ON UPDATE " + ruleToSql(updateRule);
        }

        private static String quoteAndJoin(List<String> columns) {
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < columns.size(); index++) {
                if (index > 0) {
                    builder.append(", ");
                }
                builder.append('"').append(columns.get(index)).append('"');
            }
            return builder.toString();
        }

        private static String ruleToSql(int rule) {
            // Map JDBC's DatabaseMetaData.importedKey* constants to SQL clauses.
            // Default to NO ACTION when an unknown / vendor-extended code surfaces.
            return switch (rule) {
                case DatabaseMetaData.importedKeyCascade -> "CASCADE";
                case DatabaseMetaData.importedKeyRestrict -> "RESTRICT";
                case DatabaseMetaData.importedKeySetNull -> "SET NULL";
                case DatabaseMetaData.importedKeyNoAction -> "NO ACTION";
                case DatabaseMetaData.importedKeySetDefault -> "SET DEFAULT";
                default -> "NO ACTION";
            };
        }
    }

    /** Mutable accumulator for a single FK as its multi-column metadata rows arrive. */
    private static final class ForeignKeyBuilder {

        private final String tableName;
        private final String constraintName;
        private final Map<Short, String> fkColumnsBySeq = new TreeMap<>();
        private final Map<Short, String> referencedColumnsBySeq = new TreeMap<>();
        private String referencedTable;
        private int deleteRule;
        private int updateRule;

        ForeignKeyBuilder(String tableName, String constraintName) {
            this.tableName = tableName;
            this.constraintName = constraintName;
        }

        void addColumn(
                short keySeq,
                String fkColumn,
                String pkTable,
                String pkColumn,
                int deleteRuleIn,
                int updateRuleIn) {
            fkColumnsBySeq.put(keySeq, fkColumn);
            referencedColumnsBySeq.put(keySeq, pkColumn);
            // referencedTable / rules are constant across the rows of one FK; capture from the first
            if (referencedTable == null) {
                referencedTable = pkTable;
                deleteRule = deleteRuleIn;
                updateRule = updateRuleIn;
            }
        }

        ForeignKeyDefinition build() {
            return new ForeignKeyDefinition(
                    tableName,
                    constraintName,
                    List.copyOf(fkColumnsBySeq.values()),
                    referencedTable,
                    List.copyOf(referencedColumnsBySeq.values()),
                    deleteRule,
                    updateRule);
        }
    }
}
