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

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.Priority;
import jakarta.persistence.EntityManagerFactory;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.port.DbCleanupStrategy;
import org.os890.jawelte.module.jpa.api.port.TableNameResolver;
import org.os890.jawelte.module.jpa.impl.util.JdbcAccess;

/**
 * JDBC-level {@link DbCleanupStrategy} for H2: takes the table list
 * from the active {@link TableNameResolver}, disables referential
 * integrity, issues {@code TRUNCATE TABLE} per table, then re-enables
 * referential integrity. With the default
 * {@code InformationSchemaTableNameResolver} this touches every table
 * in the {@code PUBLIC} schema — including auto-generated
 * {@code @JoinTable}s, {@code @ElementCollection} backing tables, and
 * Hibernate sequence/hilo tables — which the previous JPQL-based
 * default could not reach because it iterated only mapped
 * {@code @Entity} types.
 *
 * <p>Disabling foreign-key checks during the truncate handles schemas
 * with circular FKs without requiring topological ordering.
 *
 * <p>{@code @Priority(Integer.MAX_VALUE - 1)} — one rank ahead of
 * {@link NativeSqlDeleteDbCleanupStrategy} (which sits at
 * {@code Integer.MAX_VALUE}). Consumers running against a non-H2
 * database can either drop this jar from the test classpath or
 * register an alternative strategy at an even lower priority.
 *
 * <p>H2-specific. The {@code SET REFERENTIAL_INTEGRITY} statement
 * is an H2 extension; other providers will throw on it.
 *
 * <p>Connection sourced through {@link JdbcAccess} — borrows a
 * pooled connection without allocating an {@code EntityManager}.
 */
@Priority(Integer.MAX_VALUE - 1)
public class JdbcTruncateDbCleanupStrategy implements DbCleanupStrategy {

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public JdbcTruncateDbCleanupStrategy() {
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
        try {
            JdbcAccess.run(entityManagerFactory, connection -> {
                boolean originalAutoCommit = connection.getAutoCommit();
                try {
                    connection.setAutoCommit(true);
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
                        for (String tableName : tableNames) {
                            try {
                                statement.execute("TRUNCATE TABLE \"" + tableName + "\"");
                            } catch (SQLException perTable) {
                                RuntimeException current = primary.get();
                                RuntimeException wrapped = new RuntimeException(
                                        "Truncate failed for table '" + tableName + "' of persistence unit '"
                                                + persistenceUnitName + "'", perTable);
                                if (current == null) {
                                    primary.set(wrapped);
                                } else {
                                    current.addSuppressed(wrapped);
                                }
                            }
                        }
                        statement.execute("SET REFERENTIAL_INTEGRITY TRUE");
                    }
                } finally {
                    connection.setAutoCommit(originalAutoCommit);
                }
            });
        } catch (SQLException sqlFailure) {
            RuntimeException current = primary.get();
            RuntimeException wrapped = new RuntimeException(
                    "JDBC connection lifecycle failed during cleanup of persistence unit '"
                            + persistenceUnitName + "'", sqlFailure);
            if (current == null) {
                primary.set(wrapped);
            } else {
                current.addSuppressed(wrapped);
            }
        }
        if (primary.get() != null) {
            throw primary.get();
        }
    }
}
