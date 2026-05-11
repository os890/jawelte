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

import java.util.Map;

/**
 * Per-persistence-unit contributor of properties merged into the
 * {@code EntityManagerFactory} bootstrap configuration. jpa-module's
 * {@code JpaCdiExtension} calls
 * {@link #resolvePropertiesFor(String)} on the active implementation
 * during {@code BeforeBeanDiscovery} and merges the result on top of
 * the H2 base properties before
 * {@code Persistence.createEntityManagerFactory(...)} runs.
 *
 * <p>The contract is intentionally not bound to a specific
 * transaction model so any module can plug in (multi-data-source
 * setups, second-level cache wiring, JTA-mode property packs, …).
 *
 * <p>jpa-module/impl ships <b>no</b> default impl for this port —
 * without an active resolver, the RESOURCE_LOCAL bootstrap path
 * applies the existing H2 in-memory properties unchanged. Modules
 * that need to contribute extra properties ship the active impl at a
 * lower {@code @Priority} and register it via
 * {@code META-INF/services}.
 *
 * <p>Consumers obtain the active impl through
 * {@code TestContext.loadService(PersistencePropertyResolver.class)} —
 * the project-wide single canonical entry point for prioritized SPI
 * lookups.
 */
public interface PersistencePropertyResolver {

    /**
     * Resolve the EMF properties to merge for a given persistence
     * unit. Empty when the resolver has nothing to contribute. Never
     * {@code null}.
     *
     * @param persistenceUnitName the persistence unit name
     * @return the EMF property overrides for that persistence unit;
     *         never {@code null}, may be empty
     */
    Map<String, Object> resolvePropertiesFor(String persistenceUnitName);

    /**
     * Resolve the EMF properties to merge for a given persistence
     * unit, given the H2 base properties already accumulated by
     * {@code JpaCdiExtension} ({@code jakarta.persistence.jdbc.url},
     * {@code jakarta.persistence.jdbc.user},
     * {@code jakarta.persistence.jdbc.password},
     * {@code jakarta.persistence.jdbc.driver}, plus any
     * MP-Config-supplied prefixes). Resolvers that need to build a
     * concrete object from the URL/user/pass triple — for example a
     * JTA-mode {@code XaDataSourceWrapper} that fronts the H2
     * {@code XADataSource} — read those values here and emit the
     * built object directly under
     * {@code jakarta.persistence.jtaDataSource}.
     *
     * <p>The default implementation delegates to
     * {@link #resolvePropertiesFor(String)} for resolvers that do
     * not need the property bag.
     *
     * @param persistenceUnitName the persistence unit name
     * @param existingProperties  the property bag already accumulated
     *                            by jpa-module's bootstrap (read-only
     *                            view: callers are not expected to
     *                            mutate it)
     * @return the EMF property overrides for that persistence unit;
     *         never {@code null}, may be empty
     */
    default Map<String, Object> resolvePropertiesFor(
            String persistenceUnitName, Map<String, Object> existingProperties) {
        return resolvePropertiesFor(persistenceUnitName);
    }
}
