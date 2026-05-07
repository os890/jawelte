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

import java.util.List;

import jakarta.annotation.Priority;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.port.DbCleanupStrategy;
import org.os890.jawelte.module.jpa.api.port.EntityResolver;

/**
 * Default {@link DbCleanupStrategy} shipped by jpa-module: issues
 * provider-agnostic {@code DELETE FROM <entity-name>} JPQL for
 * every entity returned by the active {@link EntityResolver}, in
 * <strong>reverse</strong> iteration order so child rows are
 * deleted before their parents in the common acyclic-FK case.
 *
 * <p>Per-entity failures aggregate per the project exception
 * policy (TICKET-001): the first failure becomes the primary,
 * subsequent failures (and any rollback failure) are attached via
 * {@link Throwable#addSuppressed(Throwable)}. The aggregate
 * propagates from
 * {@link #cleanAllTables(String, EntityManagerFactory)}.
 *
 * <p>{@code @Priority(Integer.MAX_VALUE)} so a consumer-supplied
 * strategy at a lower priority takes over. Schemas with circular
 * FKs or large data volumes typically swap in a JDBC-truncate
 * impl that uses {@code PersistenceUnitConnectionResolver}.
 */
@Priority(Integer.MAX_VALUE)
public class JpqlDeleteDbCleanupStrategy implements DbCleanupStrategy {

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public JpqlDeleteDbCleanupStrategy() {
    }

    @Override
    public void cleanAllTables(String persistenceUnitName, EntityManagerFactory entityManagerFactory) {
        EntityResolver resolver = TestContext.loadService(EntityResolver.class);
        List<EntityType<?>> entities = resolver.resolveEntities(persistenceUnitName, entityManagerFactory);
        if (entities.isEmpty()) {
            return;
        }
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        RuntimeException primary = null;
        try {
            entityManager.getTransaction().begin();
            for (int index = entities.size() - 1; index >= 0; index--) {
                String entityName = entities.get(index).getName();
                try {
                    entityManager.createQuery("DELETE FROM " + entityName).executeUpdate();
                } catch (RuntimeException perEntity) {
                    if (primary == null) {
                        primary = new RuntimeException(
                                "Cleanup failed for entity '" + entityName + "' of persistence unit '"
                                        + persistenceUnitName + "'", perEntity);
                    } else {
                        primary.addSuppressed(perEntity);
                    }
                }
            }
            if (primary == null) {
                entityManager.getTransaction().commit();
            } else {
                try {
                    entityManager.getTransaction().rollback();
                } catch (RuntimeException rollbackFailure) {
                    primary.addSuppressed(rollbackFailure);
                }
                throw primary;
            }
        } finally {
            try {
                entityManager.close();
            } catch (RuntimeException closeFailure) {
                if (primary != null) {
                    primary.addSuppressed(closeFailure);
                }
            }
        }
    }
}
