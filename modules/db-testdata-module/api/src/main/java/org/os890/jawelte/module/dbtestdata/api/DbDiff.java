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
package org.os890.jawelte.module.dbtestdata.api;

import java.sql.Connection;
import java.util.Objects;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.JpaConfiguredPersistenceUnit;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;
import org.os890.jawelte.module.jpa.api.port.PersistenceUnitConnectionResolver;

/**
 * Static entry point for database verification. Mirrors
 * {@link DbSeed} on the read side: four factories return a
 * single-use {@link DbDiffBuilder}.
 *
 * <ul>
 *   <li>{@link #forConnection(Connection)} uses the supplied JDBC
 *       connection directly.</li>
 *   <li>{@link #forPersistenceUnit()} reads
 *       {@link PersistenceConfig#persistenceUnitName()} on the
 *       active test class; non-empty &rarr; named PU, empty / no
 *       annotation &rarr; delegate to
 *       {@link #forCurrentPersistenceUnit()}.</li>
 *   <li>{@link #forCurrentPersistenceUnit()} resolves the single
 *       persistence unit currently active on the calling thread.</li>
 *   <li>{@link #forPersistenceUnit(String)} resolves the named PU.</li>
 * </ul>
 */
public abstract class DbDiff {

    /** Utility class; not meant to be instantiated. */
    private DbDiff() {
    }

    /**
     * Use {@code connection} directly. The api never closes,
     * commits, or rolls back the connection.
     *
     * @param connection a non-{@code null} JDBC connection
     * @return a fresh {@link DbDiffBuilder}
     * @throws NullPointerException if {@code connection} is {@code null}
     */
    public static DbDiffBuilder forConnection(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        return new DbDiffBuilder(() -> connection);
    }

    /**
     * Resolve the persistence-unit connection driven by the active
     * test class's {@link PersistenceConfig#persistenceUnitName()}.
     * The annotation value is read once during jpa-module's
     * {@code beforeAll} hook and stored in
     * {@link JpaConfiguredPersistenceUnit}; when the stored value is
     * non-empty its value names the PU; when empty &mdash; including
     * the path where jpa-module is not on the classpath at all
     * &mdash; this method delegates to
     * {@link #forCurrentPersistenceUnit()}.
     *
     * @return a fresh {@link DbDiffBuilder}
     */
    public static DbDiffBuilder forPersistenceUnit() {
        String configuredName = JpaConfiguredPersistenceUnit.get();
        if (configuredName.isEmpty()) {
            return forCurrentPersistenceUnit();
        }
        return new DbDiffBuilder(() -> resolver().connectionFor(configuredName));
    }

    /**
     * Resolve the connection of the single currently active
     * persistence unit on the calling thread.
     *
     * @return a fresh {@link DbDiffBuilder}
     */
    public static DbDiffBuilder forCurrentPersistenceUnit() {
        return new DbDiffBuilder(() -> resolver().connectionForActivePersistenceUnit());
    }

    /**
     * Resolve the connection of the named persistence unit.
     *
     * @param unitName the persistence unit name
     * @return a fresh {@link DbDiffBuilder}
     */
    public static DbDiffBuilder forPersistenceUnit(String unitName) {
        Objects.requireNonNull(unitName, "unitName");
        return new DbDiffBuilder(() -> resolver().connectionFor(unitName));
    }

    private static PersistenceUnitConnectionResolver resolver() {
        PersistenceUnitConnectionResolver resolver = TestContext.loadService(PersistenceUnitConnectionResolver.class);
        if (resolver == null) {
            throw new IllegalStateException(
                    "No PersistenceUnitConnectionResolver registered. Is jpa-module "
                            + "(or another resolver impl) on the classpath?");
        }
        return resolver;
    }
}
