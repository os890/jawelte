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
package org.os890.jawelte.tests.jpa.scenario31;

import jakarta.annotation.Priority;
import jakarta.persistence.EntityManagerFactory;

import org.os890.jawelte.module.jpa.api.port.DbCleanupStrategy;

/**
 * Test-only {@link DbCleanupStrategy} at {@code @Priority(100)} — wins over
 * the default impls (which sit at {@code @Priority(Integer.MAX_VALUE)} and
 * {@code @Priority(Integer.MAX_VALUE - 1)}). Counts how many times
 * {@code cleanAllTables} is called so the test can assert the swap took
 * effect.
 */
@Priority(100)
public class CountingDbCleanupStrategy implements DbCleanupStrategy {

    /** No-arg constructor required by ServiceLoader. */
    public CountingDbCleanupStrategy() {
    }

    @Override
    public void cleanAllTables(String persistenceUnitName, EntityManagerFactory entityManagerFactory) {
        // Custom impl is a no-op — test only cares that this method was reached.
    }
}
