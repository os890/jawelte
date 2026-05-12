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
import org.os890.jawelte.module.jpa.api.port.PersistenceUnitConnectionResolver;

/**
 * Static entry point for database fixtures. Three factories return
 * a single-use {@link DbSeedBuilder}:
 *
 * <ul>
 *   <li>{@link #forConnection(Connection)} uses the supplied JDBC
 *       connection directly — the api never closes, commits, or
 *       rolls it back.</li>
 *   <li>{@link #forPersistenceUnit()} resolves the single persistence
 *       unit currently active on the calling thread via the
 *       project-wide
 *       {@link PersistenceUnitConnectionResolver}. Multiple active
 *       PUs raise {@link IllegalStateException}.</li>
 *   <li>{@link #forPersistenceUnit(String)} resolves the named PU.</li>
 * </ul>
 */
public abstract class DbSeed {

    /** Utility class; not meant to be instantiated. */
    private DbSeed() {
    }

    /**
     * Use {@code connection} directly. The api never closes,
     * commits, or rolls back the connection; the caller owns the
     * transaction lifecycle.
     *
     * @param connection a non-{@code null} JDBC connection
     * @return a fresh {@link DbSeedBuilder}
     * @throws NullPointerException if {@code connection} is {@code null}
     */
    public static DbSeedBuilder forConnection(Connection connection) {
        Objects.requireNonNull(connection, "connection");
        return new DbSeedBuilder(() -> connection);
    }

    /**
     * Resolve the connection of the single currently active
     * persistence unit on the calling thread. The active
     * {@link PersistenceUnitConnectionResolver}'s
     * {@code connectionForActivePersistenceUnit()} provides the
     * connection — it raises {@link IllegalStateException} when
     * zero or more than one PU is active.
     *
     * @return a fresh {@link DbSeedBuilder}
     */
    public static DbSeedBuilder forPersistenceUnit() {
        return new DbSeedBuilder(() -> resolver().connectionForActivePersistenceUnit());
    }

    /**
     * Resolve the connection of the named persistence unit via the
     * active {@link PersistenceUnitConnectionResolver}.
     *
     * @param unitName the persistence unit name
     * @return a fresh {@link DbSeedBuilder}
     */
    public static DbSeedBuilder forPersistenceUnit(String unitName) {
        Objects.requireNonNull(unitName, "unitName");
        return new DbSeedBuilder(() -> resolver().connectionFor(unitName));
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
