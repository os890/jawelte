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

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
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
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.SchemaManager;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.port.DbCleanupStrategy;
import org.os890.jawelte.module.jpa.api.port.TableNameResolver;
import org.os890.jawelte.module.jpa.impl.util.JdbcAccess;

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
 *       rules) so an equivalent {@code ADD CONSTRAINT} can be
 *       reconstructed (see the portability note below — this is a
 *       metadata-level reconstruction, not the original DDL).</li>
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
 * <p><strong>Failure handling.</strong> An anonymous foreign key
 * (null {@code FK_NAME}) can't be dropped by name, so it is skipped
 * with a logged {@code WARNING} naming the table; if it does not
 * block the deletes the cleanup still commits. Any drop / delete /
 * re-add failure, however, means the fast path could not guarantee an
 * empty database, so the transaction is rolled back and the cleanup
 * <strong>falls back to dropping and recreating the schema</strong>
 * via {@link jakarta.persistence.EntityManagerFactory#getSchemaManager()}
 * ({@code drop(false)} then {@code create(false)}). That guarantees a
 * clean database and restores it with named foreign keys, so the next
 * cleanup uses the fast path again (self-healing). The original
 * failure is logged at {@code WARNING}; an exception is thrown only if
 * the schema recreate itself fails.
 *
 * <p><strong>Fallback scope.</strong> The schema recreate covers the
 * persistence unit's mapped objects (entities and their
 * Hibernate-managed {@code @JoinTable} / {@code @ElementCollection} /
 * sequence tables). Unmapped tables with no JPA mapping (e.g.
 * trigger-populated audit logs) are cleaned by the fast path but not
 * by the recreate fallback; the fallback only runs on the rare
 * un-droppable-FK error path.
 *
 * <p><strong>NOT pre-registered</strong> via {@code META-INF/services}.
 * The H2-targeted {@link JdbcTruncateDbCleanupStrategy} is the only
 * {@link DbCleanupStrategy} jpa-module pre-registers; consumers running
 * against a non-H2 database that lacks {@code TRUNCATE} or
 * {@code SET REFERENTIAL_INTEGRITY} drop the appropriate
 * {@code META-INF/services/org.os890.jawelte.module.jpa.api.port.DbCleanupStrategy}
 * file in their own classpath pointing at this class — typically at a
 * lower numeric {@code @Priority} than the
 * {@code Integer.MAX_VALUE - 1} of {@link JdbcTruncateDbCleanupStrategy}
 * so the swap takes effect. The {@code @Priority(Integer.MAX_VALUE)}
 * carried here is the "lose every priority sort by default" rank — it
 * matters only when both impls happen to be on the classpath; the swap
 * itself is the consumer's explicit registration.
 *
 * <p><strong>Re-add fidelity (portability).</strong> The re-added
 * constraint is reconstructed from JDBC metadata
 * ({@link DatabaseMetaData#getImportedKeys}), not captured as the
 * original DDL text, and the {@code ON DELETE} / {@code ON UPDATE}
 * clauses are always emitted. Consequences when targeting a non-H2
 * database:
 * <ul>
 *   <li>A referential rule the driver reports but its {@code ALTER
 *       TABLE ... ADD CONSTRAINT} grammar rejects (for example {@code
 *       SET DEFAULT}, or an explicit {@code ON UPDATE} clause on
 *       engines that disallow it) fails the re-add step. That failure
 *       is aggregated and triggers the drop-and-recreate schema
 *       fallback, so the database still ends up clean — but the
 *       reconstructed rule, not the original, is what gets re-applied.</li>
 *   <li>{@code SET DEFAULT} loses the column's default-value
 *       expression: only the rule is reconstructed, not the default it
 *       resolves against.</li>
 *   <li>A vendor-extended or unrecognised JDBC rule code degrades to
 *       {@code NO ACTION} (see {@code ForeignKeyDefinition.ruleToSql}),
 *       silently relaxing the constraint's original semantics.</li>
 * </ul>
 * Verified against H2 2.3.232, the suite's target: it accepts every
 * emitted clause ({@code CASCADE}, {@code SET NULL}, {@code SET
 * DEFAULT}, {@code RESTRICT}, {@code NO ACTION}) on both {@code ON
 * DELETE} and {@code ON UPDATE} and round-trips the drop / re-add, so
 * none of the above bites on H2. (The historical "H2 lacks {@code SET
 * DEFAULT}" limitation was an H2 1.x trait.) A consumer swapping this
 * strategy onto a different engine that hits one of the cases above
 * relies on the recreate fallback rather than a faithful re-add.
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
 *
 * <p>Connection sourced through {@link JdbcAccess} — borrows a
 * pooled connection without allocating an {@code EntityManager}.
 */
@Priority(Integer.MAX_VALUE)
public class NativeSqlDeleteDbCleanupStrategy implements DbCleanupStrategy {

    private static final Logger LOG = System.getLogger(NativeSqlDeleteDbCleanupStrategy.class.getName());

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public NativeSqlDeleteDbCleanupStrategy() {
    }

    @Override
    public void cleanAllTables(String persistenceUnitName, EntityManagerFactory entityManagerFactory) {
        TableNameResolver tableNameResolver = TestContext.loadService(TableNameResolver.class);
        // Migration-tool bookkeeping and anything else the deployment
        // declared off-limits: emptying a schema-migration history while
        // leaving the DDL it describes is never the intended outcome.
        List<String> tableNames = CleanupTableExclusions.apply(
                tableNameResolver.resolveTableNames(persistenceUnitName, entityManagerFactory));
        if (tableNames.isEmpty()) {
            return;
        }
        AtomicReference<RuntimeException> primary = new AtomicReference<>();
        List<String> advisoryWarnings = new ArrayList<>();
        try {
            JdbcAccess.run(entityManagerFactory, connection -> {
                boolean originalAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    runDropDeleteAndReadd(
                            connection, tableNames, persistenceUnitName, primary, advisoryWarnings);
                    if (primary.get() == null) {
                        connection.commit();
                    } else {
                        try {
                            connection.rollback();
                        } catch (SQLException rollbackFailure) {
                            primary.get().addSuppressed(rollbackFailure);
                        }
                    }
                } finally {
                    connection.setAutoCommit(originalAutoCommit);
                }
            });
        } catch (SQLException sqlFailure) {
            aggregateFailure(primary,
                    "JDBC connection lifecycle failed during cleanup of persistence unit '"
                            + persistenceUnitName + "'", sqlFailure);
        }

        // Anonymous FKs that couldn't be dropped by name are informational:
        // if they didn't block the deletes the cleanup already committed.
        for (String warning : advisoryWarnings) {
            LOG.log(Level.WARNING, warning);
        }

        RuntimeException fastPathFailure = primary.get();
        if (fastPathFailure == null) {
            return;
        }

        // The fast path failed and was rolled back, so the database may still
        // hold rows. Fall back to dropping and recreating the schema: that
        // guarantees an empty database AND restores it with named foreign keys
        // (so the next cleanup uses the fast path again). Only surface an
        // exception if the recreate itself fails.
        LOG.log(Level.WARNING,
                "Native-SQL drop/delete/re-add cleanup failed for persistence unit '"
                        + persistenceUnitName + "'; rolled back and falling back to a schema "
                        + "drop+recreate. Cause: " + fastPathFailure.getMessage(),
                fastPathFailure);
        try {
            recreateSchema(entityManagerFactory);
        } catch (RuntimeException recreateFailure) {
            recreateFailure.addSuppressed(fastPathFailure);
            throw recreateFailure;
        }
    }

    /**
     * Drop and recreate the persistence unit's mapped schema via the
     * JPA {@link SchemaManager}. {@code drop(false)} removes the mapped
     * tables (and their constraints), {@code create(false)} re-creates
     * them — Hibernate emits named foreign keys, so a subsequent
     * cleanup can take the fast drop-by-name path.
     */
    private static void recreateSchema(EntityManagerFactory entityManagerFactory) {
        SchemaManager schemaManager = entityManagerFactory.getSchemaManager();
        schemaManager.drop(false);
        schemaManager.create(false);
    }

    private static void runDropDeleteAndReadd(
            Connection connection,
            List<String> tableNames,
            String persistenceUnitName,
            AtomicReference<RuntimeException> primary,
            List<String> advisoryWarnings) throws SQLException {
        List<ForeignKeyDefinition> capturedForeignKeys =
                captureForeignKeys(connection, tableNames, persistenceUnitName, advisoryWarnings);
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
    }

    private static List<ForeignKeyDefinition> captureForeignKeys(
            Connection connection,
            List<String> tableNames,
            String persistenceUnitName,
            List<String> advisoryWarnings) throws SQLException {
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
                        // Anonymous FK — can't drop by name; record as advisory
                        // and skip. If it doesn't block the deletes the cleanup
                        // still commits; if it does, the resulting delete failure
                        // triggers the schema-recreate fallback (which heals it
                        // into a named constraint).
                        advisoryWarnings.add(
                                "Anonymous foreign key on table '" + tableName
                                        + "' of persistence unit '" + persistenceUnitName
                                        + "' cannot be dropped by name; skipped (drop-and-readd "
                                        + "requires a named constraint)");
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

    /**
     * Captured FK definition — enough JDBC-metadata state to
     * reconstruct an equivalent {@code ADD CONSTRAINT}. This is a
     * reconstruction, not the original DDL text; see the class-level
     * "Re-add fidelity (portability)" note for the lossy cases
     * ({@code SET DEFAULT} default values, vendor rule codes).
     */
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
    private static class ForeignKeyBuilder {

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
