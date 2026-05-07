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
import jakarta.persistence.metamodel.EntityType;

/**
 * Returns the list of entities to consider for a persistence unit.
 * Used by {@link DbCleanupStrategy} to drive per-method cleanup, and
 * reusable by other modules that need the same answer (e.g. a future
 * db-testdata module that seeds fixtures).
 *
 * <p>The default implementation shipped by jpa-module/impl returns
 * every {@link EntityType} from the JPA metamodel. Consumers who want
 * to filter — for instance, exclude reference / lookup tables from
 * cleanup — provide their own impl at a lower {@code @Priority} and
 * register it via {@code META-INF/services}.
 *
 * <p>Consumers obtain the active impl through
 * {@code TestContext.loadService(EntityResolver.class)} — the
 * project-wide single canonical entry point for prioritized SPI
 * lookups.
 */
public interface EntityResolver {

    /**
     * Resolve the entities for a given persistence unit.
     *
     * @param persistenceUnitName  the persistence unit name
     * @param entityManagerFactory the {@link EntityManagerFactory} for
     *                             that persistence unit
     * @return the entity types to consider; never {@code null}, may be
     *         empty
     */
    List<EntityType<?>> resolveEntities(String persistenceUnitName, EntityManagerFactory entityManagerFactory);
}
