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

import org.os890.jawelte.module.jpa.api.port.TableNameResolver;

/**
 * Test-only {@link TableNameResolver} at {@code @Priority(100)} — wins over
 * the addon's INFORMATION_SCHEMA-backed default. Returns an empty list (the
 * cleanup strategies treat that as "nothing to clean"); the test only cares
 * that {@code TestContext.loadService} routes to this impl.
 */
@Priority(100)
public class CountingTableNameResolver implements TableNameResolver {

    /** No-arg constructor required by ServiceLoader. */
    public CountingTableNameResolver() {
    }

    @Override
    public List<String> resolveTableNames(String persistenceUnitName, EntityManagerFactory entityManagerFactory) {
        return List.of();
    }
}
