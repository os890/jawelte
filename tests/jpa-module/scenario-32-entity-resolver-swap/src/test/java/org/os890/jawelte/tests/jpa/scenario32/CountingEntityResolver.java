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
package org.os890.jawelte.tests.jpa.scenario32;

import java.util.List;

import jakarta.annotation.Priority;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;

import org.os890.jawelte.module.jpa.api.port.EntityResolver;

/**
 * Test-only {@link EntityResolver} at {@code @Priority(100)} — wins over the
 * addon's metamodel-backed impl. Returns an empty list (the cleanup-via-JPQL
 * path treats that as "nothing to delete"); the test only cares that
 * {@code TestContext.loadService} routes to this impl.
 */
@Priority(100)
public class CountingEntityResolver implements EntityResolver {

    /** No-arg constructor required by ServiceLoader. */
    public CountingEntityResolver() {
    }

    @Override
    public List<EntityType<?>> resolveEntities(String persistenceUnitName, EntityManagerFactory entityManagerFactory) {
        return List.of();
    }
}
