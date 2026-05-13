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

import java.sql.Connection;

/**
 * Returns a managed JDBC {@link Connection} for a given persistence
 * unit. Used by JDBC-flavoured {@link DbCleanupStrategy}
 * implementations (e.g. truncate-based) and by other modules that
 * need to seed or verify against the same managed connection a
 * {@code @Transactional} test method is using.
 *
 * <p>Consumers obtain the active impl through
 * {@code TestContext.loadService(PersistenceUnitConnectionResolver.class)} —
 * the project-wide single canonical entry point for prioritized SPI
 * lookups.
 */
public interface PersistenceUnitConnectionResolver {

    /**
     * Resolve the JDBC {@link Connection} for the named persistence
     * unit. The implementation chooses how to map persistence units to
     * connections (e.g. by unwrapping an active
     * {@code EntityManager} or by constructing one from the EMF's
     * properties).
     *
     * @param persistenceUnitName the persistence unit name
     * @return the JDBC connection; never {@code null}
     */
    Connection connectionFor(String persistenceUnitName);

    /**
     * Resolve the JDBC {@link Connection} for the **only** persistence
     * unit currently active on the calling thread. Intended for
     * callers that did not pass an explicit persistence-unit name
     * (e.g. {@code DbSeed.forPersistenceUnit()}) and where the
     * scenario only ever uses one PU.
     *
     * <p>The resolver throws {@link IllegalStateException} when:
     *
     * <ul>
     *   <li>no persistence unit is active on the calling thread —
     *       typically because the call is outside an active
     *       {@code @Transactional} or
     *       {@code UserTransaction.begin()} boundary, or</li>
     *   <li>more than one persistence unit is active and the caller
     *       did not disambiguate; the caller must then use
     *       {@link #connectionFor(String)} with an explicit name.</li>
     * </ul>
     *
     * @return the JDBC connection for the single active PU; never
     *         {@code null}
     */
    Connection connectionForActivePersistenceUnit();
}
