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
package org.os890.jawelte.module.jpa.api.port;

import java.util.List;

import jakarta.persistence.EntityManagerFactory;

/**
 * Returns the list of database table names to consider for cleanup
 * within a given persistence unit. The {@link DbCleanupStrategy}
 * implementations consult this port to learn what to wipe.
 *
 * <p>This is intentionally a <em>schema-level</em> abstraction rather
 * than a JPA-metamodel one: tables populated by triggers, managed by
 * external migration tooling (Flyway / Liquibase), Hibernate sequence
 * / hilo bookkeeping tables, and {@code @JoinTable} /
 * {@code @ElementCollection} backing tables are all real targets of
 * per-method cleanup but are NOT mapped as JPA {@code @Entity} types.
 * Returning the schema's actual table names lets cleanup reach every
 * one of them.
 *
 * <p>The default implementation shipped by jpa-module/impl
 * ({@code InformationSchemaTableNameResolver}) walks H2's
 * {@code INFORMATION_SCHEMA.TABLES} for the {@code PUBLIC} schema.
 * An optional metamodel-backed alternative
 * ({@code JpaMetamodelTableNameResolver}) ships in jpa-module/impl
 * but is <em>not</em> pre-registered through {@code META-INF/services};
 * consumers who explicitly want metamodel-only cleanup register it
 * themselves and let the priority sort hand them the active impl.
 *
 * <p>Consumers obtain the active impl through
 * {@code TestContext.loadService(TableNameResolver.class)} — the
 * project-wide single canonical entry point for prioritized SPI lookups.
 */
public interface TableNameResolver {

    /**
     * Resolve the table names targeted by per-method cleanup for a
     * given persistence unit.
     *
     * @param persistenceUnitName  the persistence unit name
     * @param entityManagerFactory the {@link EntityManagerFactory} for
     *                             that persistence unit (impls that
     *                             need a JDBC {@link java.sql.Connection}
     *                             unwrap a fresh {@link jakarta.persistence.EntityManager}
     *                             from the factory)
     * @return the table names to consider; never {@code null}, may be empty
     */
    List<String> resolveTableNames(String persistenceUnitName, EntityManagerFactory entityManagerFactory);
}
