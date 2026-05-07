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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.Priority;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.hibernate.Session;
import org.os890.jawelte.module.jpa.api.port.DbCleanupStrategy;

/**
 * JDBC-level {@link DbCleanupStrategy} for H2: walks
 * {@code INFORMATION_SCHEMA.TABLES}, disables referential integrity,
 * issues a {@code TRUNCATE TABLE} per public-schema table, then
 * re-enables referential integrity. Touches every table in the
 * {@code PUBLIC} schema — including auto-generated
 * {@code @JoinTable}s, {@code @ElementCollection} backing tables,
 * and Hibernate sequence/hilo tables — which the JPQL-based
 * default cannot reach because it iterates only mapped
 * {@code @Entity} types.
 *
 * <p>Disabling foreign-key checks during the truncate handles
 * schemas with circular FKs without requiring topological
 * ordering.
 *
 * <p>{@code @Priority(Integer.MAX_VALUE - 1)} — one rank ahead of
 * {@link JpqlDeleteDbCleanupStrategy} (which sits at
 * {@code Integer.MAX_VALUE}). Consumers running against a non-H2
 * database can either drop this jar from the test classpath or
 * register an alternative strategy at an even lower priority.
 *
 * <p>H2-specific. The {@code SET REFERENTIAL_INTEGRITY} statement
 * is an H2 extension; the {@code INFORMATION_SCHEMA.TABLES} query
 * uses H2-style {@code TABLE_SCHEMA = 'PUBLIC'} filtering. Other
 * providers will throw on either step.
 */
@Priority(Integer.MAX_VALUE - 1)
public class JdbcTruncateDbCleanupStrategy implements DbCleanupStrategy {

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public JdbcTruncateDbCleanupStrategy() {
    }

    @Override
    public void cleanAllTables(String persistenceUnitName, EntityManagerFactory entityManagerFactory) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        AtomicReference<RuntimeException> primary = new AtomicReference<>();
        try {
            Session session = entityManager.unwrap(Session.class);
            session.doWork(connection -> {
                boolean originalAutoCommit = connection.getAutoCommit();
                try {
                    connection.setAutoCommit(true);
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
                        List<String> tableNames = listPublicTables(statement);
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
            if (primary.get() != null) {
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

    private static List<String> listPublicTables(Statement statement) throws SQLException {
        List<String> tableNames = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'")) {
            while (resultSet.next()) {
                tableNames.add(resultSet.getString(1));
            }
        }
        return tableNames;
    }
}
