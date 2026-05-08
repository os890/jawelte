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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.port.DbCleanupStrategy;

/**
 * A test-only {@link CountingDbCleanupStrategy} at {@code @Priority(100)}
 * registered through {@code META-INF/services} wins the
 * {@code TestContext.loadService} priority sort over jpa-module's
 * default impls — locking in the project-wide swappability claim for the
 * cleanup port.
 */
@EnableTestBeans
public class Scenario31Test {

    /** No-arg constructor for CDI. */
    public Scenario31Test() {
    }

    /** TestContext.loadService returns the @Priority(100) test-only impl. */
    @Test
    public void customDbCleanupStrategyWinsThePrioritySort() {
        DbCleanupStrategy active = TestContext.loadService(DbCleanupStrategy.class);

        assertThat(active)
                .as("a test-only DbCleanupStrategy at @Priority(100) must win over the "
                        + "addon's @Priority(MAX_VALUE - 1) JdbcTruncate / @Priority(MAX_VALUE) JpqlDelete")
                .isInstanceOf(CountingDbCleanupStrategy.class);
    }
}
