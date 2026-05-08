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

import jakarta.annotation.Priority;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.hibernate.Session;
import org.os890.jawelte.module.jpa.api.port.TableNameResolver;

/**
 * Default {@link TableNameResolver} shipped by jpa-module — queries
 * H2's {@code INFORMATION_SCHEMA.TABLES} for tables in the
 * {@code PUBLIC} schema. Mirrors the POC's approach: cleanup targets
 * are derived from the actual database schema, not from the JPA
 * metamodel, so unmapped tables (trigger-populated audit logs,
 * {@code @JoinTable} / {@code @ElementCollection} backing tables,
 * Hibernate sequence / hilo bookkeeping, …) are included automatically.
 *
 * <p>{@code @Priority(Integer.MAX_VALUE)} — the absolute fallback in
 * the project's "lowest priority wins" sort. Consumers shipping an
 * alternative impl (a metamodel-only filter, a hand-curated table
 * allowlist, a non-H2 schema query, …) register at a lower numeric
 * {@code @Priority}.
 *
 * <p>H2-specific. The {@code INFORMATION_SCHEMA.TABLES} query uses
 * H2-style {@code TABLE_SCHEMA = 'PUBLIC'} filtering. Other providers
 * surface different system-catalog views; consumers running against
 * those swap in their own resolver.
 */
@Priority(Integer.MAX_VALUE)
public class InformationSchemaTableNameResolver implements TableNameResolver {

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public InformationSchemaTableNameResolver() {
    }

    @Override
    public List<String> resolveTableNames(String persistenceUnitName, EntityManagerFactory entityManagerFactory) {
        List<String> tableNames = new ArrayList<>();
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            Session session = entityManager.unwrap(Session.class);
            session.doWork(connection -> {
                try (Statement statement = connection.createStatement();
                        ResultSet resultSet = statement.executeQuery(
                                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'")) {
                    while (resultSet.next()) {
                        tableNames.add(resultSet.getString(1));
                    }
                } catch (SQLException sqlFailure) {
                    throw new RuntimeException(
                            "INFORMATION_SCHEMA query for cleanup-target tables failed for persistence unit '"
                                    + persistenceUnitName + "'",
                            sqlFailure);
                }
            });
        } finally {
            entityManager.close();
        }
        return List.copyOf(tableNames);
    }
}
