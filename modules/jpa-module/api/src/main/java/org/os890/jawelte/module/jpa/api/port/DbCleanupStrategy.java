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

import jakarta.persistence.EntityManagerFactory;

/**
 * Pluggable per-method database cleanup. Called by jpa-module's
 * {@code JpaLifecycleAdapter.afterEach} for every active
 * persistence unit after the {@code AfterTestTransaction} event has
 * fired, and skipped when
 * {@code @PersistenceConfig.fileMode=true}.
 *
 * <p>The default implementation shipped by jpa-module/impl is a
 * JDBC {@code TRUNCATE TABLE}-based strategy that disables referential
 * integrity, truncates every table returned by the active
 * {@link TableNameResolver}, and re-enables referential integrity. A
 * native-SQL {@code DELETE} fallback ({@code NativeSqlDeleteDbCleanupStrategy})
 * sits at the lowest priority for non-H2 setups.
 *
 * <p>Consumers obtain the active impl through
 * {@code TestContext.loadService(DbCleanupStrategy.class)} — the
 * project-wide single canonical entry point for prioritized SPI
 * lookups.
 */
public interface DbCleanupStrategy {

    /**
     * Empty every table belonging to the named persistence unit.
     * Called once per active persistence unit per test method.
     *
     * <p>Per-table failures aggregate per the project's exception
     * policy: the first failure becomes the primary, subsequent
     * failures are attached via
     * {@link Throwable#addSuppressed(Throwable)}.
     *
     * @param persistenceUnitName    the persistence unit name to clean
     * @param entityManagerFactory   the {@link EntityManagerFactory}
     *                               for that persistence unit; the
     *                               implementation may use it to open
     *                               an {@code EntityManager}, walk the
     *                               metamodel, or read the JDBC
     *                               connection
     */
    void cleanAllTables(String persistenceUnitName, EntityManagerFactory entityManagerFactory);
}
