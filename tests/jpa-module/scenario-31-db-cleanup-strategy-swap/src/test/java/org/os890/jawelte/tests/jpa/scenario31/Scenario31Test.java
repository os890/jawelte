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

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.port.DbCleanupStrategy;

/**
 * A test-only {@link TestScenarioCountingDbCleanupStrategy} at {@code @Priority(100)}
 * registered through {@code META-INF/services} wins the
 * {@code TestContext.loadService} priority sort over jpa-module's
 * default impls AND the framework's per-method cleanup actually
 * delegates to it. Both halves matter:
 *
 * <ul>
 *   <li>Method 1 verifies SPI resolution (the priority sort).</li>
 *   <li>Method 2 persists a real row inside a {@code @Transactional}
 *       method, triggering jpa-module's per-method cleanup. Method 3
 *       then asserts the swapped impl's counter was bumped — proves
 *       the lifecycle's delegation, not just the SPI's return value.
 *       Without the counter check the test would silently pass even
 *       if {@code JpaLifecycleAdapter.runCleanup} hard-coded a default
 *       impl bypassing {@code TestContext.loadService} entirely
 *       (punch-list §8.2 / §9.2).</li>
 * </ul>
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Scenario31Test {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public Scenario31Test() {
    }

    /** TestContext.loadService returns the @Priority(100) test-only impl. */
    @Test
    @Order(1)
    public void customDbCleanupStrategyWinsThePrioritySort() {
        DbCleanupStrategy active = TestContext.loadService(DbCleanupStrategy.class);

        assertThat(active)
                .as("a test-only DbCleanupStrategy at @Priority(100) must win over the "
                        + "addon's @Priority(MAX_VALUE - 1) JdbcTruncate / @Priority(MAX_VALUE) JpqlDelete")
                .isInstanceOf(TestScenarioCountingDbCleanupStrategy.class);
    }

    /** Persist + commit in a @Transactional method — drives the per-method-cleanup hook. */
    @Test
    @Order(2)
    @Transactional
    public void persistDrivesAfterEachCleanup() {
        TestScenarioCountingDbCleanupStrategy.INVOCATION_COUNT.set(0);
        entityManager.persist(new Marker());
        entityManager.flush();
    }

    /** Method 2's afterEach must have invoked the swapped strategy. */
    @Test
    @Order(3)
    public void lifecycleDelegatedToTheSwappedStrategy() {
        assertThat(TestScenarioCountingDbCleanupStrategy.INVOCATION_COUNT.get())
                .as("JpaLifecycleAdapter.afterEach must resolve the cleanup strategy via "
                        + "TestContext.loadService (NOT a hard-coded default) so the swapped "
                        + "impl is the one that actually runs. Closes punch-list §8.2.")
                .isGreaterThanOrEqualTo(1);
    }
}
