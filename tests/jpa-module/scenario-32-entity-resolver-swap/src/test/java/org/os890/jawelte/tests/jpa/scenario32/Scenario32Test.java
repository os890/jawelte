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
import org.os890.jawelte.module.jpa.api.port.TableNameResolver;

/**
 * A test-only {@link TestScenarioCountingTableNameResolver} at {@code @Priority(100)}
 * registered through {@code META-INF/services} wins the
 * {@code TestContext.loadService} priority sort over jpa-module's
 * default impl AND the framework's per-method cleanup actually
 * delegates to it. Both halves matter:
 *
 * <ul>
 *   <li>Method 1 verifies SPI resolution (the priority sort).</li>
 *   <li>Method 2 persists a real row inside a {@code @Transactional}
 *       method, triggering jpa-module's per-method cleanup. Method 3
 *       then asserts the swapped resolver's counter was bumped — proves
 *       {@code JdbcTruncateDbCleanupStrategy} actually consults the
 *       SPI-resolved {@code TableNameResolver}, not a hard-coded
 *       {@code new InformationSchemaTableNameResolver()} (punch-list
 *       §8.2 / §9.2).</li>
 * </ul>
 *
 * <p>(The directory name keeps the original "entity-resolver-swap"
 * label for branch traceability; the port itself is now
 * {@code TableNameResolver}.)
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Scenario32Test {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public Scenario32Test() {
    }

    /** TestContext.loadService returns the @Priority(100) test-only impl. */
    @Test
    @Order(1)
    public void customTableNameResolverWinsThePrioritySort() {
        TableNameResolver active = TestContext.loadService(TableNameResolver.class);

        assertThat(active)
                .as("a test-only TableNameResolver at @Priority(100) must win over the "
                        + "addon's @Priority(MAX_VALUE) InformationSchemaTableNameResolver")
                .isInstanceOf(TestScenarioCountingTableNameResolver.class);
    }

    /** Persist + commit in a @Transactional method — drives the cleanup hook. */
    @Test
    @Order(2)
    @Transactional
    public void persistDrivesTableNameResolverThroughCleanup() {
        TestScenarioCountingTableNameResolver.INVOCATION_COUNT.set(0);
        entityManager.persist(new Marker());
        entityManager.flush();
    }

    /**
     * Method 2's afterEach cleanup must have consulted the swapped
     * resolver. Without this assertion, a regression where
     * {@code JdbcTruncateDbCleanupStrategy} hard-codes
     * {@code new InformationSchemaTableNameResolver()} bypassing
     * the SPI would not be caught.
     */
    @Test
    @Order(3)
    public void jdbcTruncateDelegatedToTheSwappedResolver() {
        assertThat(TestScenarioCountingTableNameResolver.INVOCATION_COUNT.get())
                .as("JdbcTruncateDbCleanupStrategy must resolve the table-name resolver via "
                        + "TestContext.loadService (NOT instantiate the default directly) so "
                        + "the swapped impl is the one that actually walks the schema. "
                        + "Closes punch-list §8.2.")
                .isGreaterThanOrEqualTo(1);
    }
}
