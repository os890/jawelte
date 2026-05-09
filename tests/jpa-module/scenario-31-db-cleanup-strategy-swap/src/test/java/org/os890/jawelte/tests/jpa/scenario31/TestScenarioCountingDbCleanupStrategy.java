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

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.Priority;
import jakarta.persistence.EntityManagerFactory;

import org.os890.jawelte.module.jpa.api.port.DbCleanupStrategy;

/**
 * Test-only {@link DbCleanupStrategy} at {@code @Priority(100)} — wins over
 * the default impls (which sit at {@code @Priority(Integer.MAX_VALUE)} and
 * {@code @Priority(Integer.MAX_VALUE - 1)}). Counts how many times
 * {@code cleanAllTables} is called so the test can prove
 * the lifecycle actually delegates to the SPI-resolved impl, not just
 * that the SPI returns it.
 */
@Priority(100)
public class TestScenarioCountingDbCleanupStrategy implements DbCleanupStrategy {

    /**
     * Static so the test can read it across CDI scopes — {@code TestContext.loadService}
     * may instantiate this strategy multiple times. Reset is a per-suite
     * concern handled by JUnit-fresh class loaders, not by this counter.
     */
    public static final AtomicInteger INVOCATION_COUNT = new AtomicInteger();

    /** No-arg constructor required by ServiceLoader. */
    public TestScenarioCountingDbCleanupStrategy() {
    }

    @Override
    public void cleanAllTables(String persistenceUnitName, EntityManagerFactory entityManagerFactory) {
        // Count the call. The body itself is a no-op — the test asserts
        // delegation happened, not that any specific cleanup work ran.
        INVOCATION_COUNT.incrementAndGet();
    }
}
