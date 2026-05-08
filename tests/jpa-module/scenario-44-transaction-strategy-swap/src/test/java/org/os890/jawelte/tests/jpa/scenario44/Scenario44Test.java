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
package org.os890.jawelte.tests.jpa.scenario44;

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
import org.os890.jawelte.module.jpa.api.port.TransactionStrategy;

/**
 * A test-only {@link CountingTransactionStrategy} at
 * {@code @Priority(100)} registered through {@code META-INF/services}
 * wins the {@code TestContext.loadService} priority sort over jpa-module's
 * default {@code DefaultResourceLocalTransactionStrategy} AND the
 * framework's {@code TransactionalInterceptor} actually delegates to it.
 * Both halves matter:
 *
 * <ul>
 *   <li>Method 1 verifies SPI resolution (the priority sort).</li>
 *   <li>Method 2 runs a {@code @Transactional} persist on a service
 *       bean. Method 3 then asserts the swapped strategy's counters
 *       (begin / commit) were bumped — proves
 *       {@code TransactionalInterceptor} resolves the strategy via
 *       {@code TestContext.loadService} (NOT via a hard-coded
 *       default), so the same mechanism a future jta-module would
 *       use to plug in a JTA strategy is empirically tested
 *       (punch-list §8.2 / §9.2).</li>
 * </ul>
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Scenario44Test {

    @Inject
    private MarkerService markerService;

    /** No-arg constructor for CDI. */
    public Scenario44Test() {
    }

    /** TestContext.loadService returns the @Priority(100) test-only impl. */
    @Test
    @Order(1)
    public void customTransactionStrategyWinsThePrioritySort() {
        TransactionStrategy active = TestContext.loadService(TransactionStrategy.class);

        assertThat(active)
                .as("a test-only TransactionStrategy at @Priority(100) must win over the "
                        + "addon's @Priority(MAX_VALUE) DefaultResourceLocalTransactionStrategy")
                .isInstanceOf(CountingTransactionStrategy.class);
    }

    /** A @Transactional service call drives the interceptor's begin / commit through the strategy. */
    @Test
    @Order(2)
    public void transactionalCallDrivesStrategyBeginAndCommit() {
        CountingTransactionStrategy.BEGIN_COUNT.set(0);
        CountingTransactionStrategy.COMMIT_COUNT.set(0);
        markerService.persistMarker();
    }

    /**
     * Method 2's @Transactional call must have driven the swapped
     * strategy's begin and commit. Without these assertions, a regression
     * where {@code TransactionalInterceptor} hard-codes the default
     * strategy bypassing the SPI would silently pass the @Order(1)
     * assertion above.
     */
    @Test
    @Order(3)
    public void interceptorDelegatedToTheSwappedStrategy() {
        assertThat(CountingTransactionStrategy.BEGIN_COUNT.get())
                .as("TransactionalInterceptor must resolve the strategy via "
                        + "TestContext.loadService and call begin() on it. Closes punch-list §8.2.")
                .isGreaterThanOrEqualTo(1);
        assertThat(CountingTransactionStrategy.COMMIT_COUNT.get())
                .as("the same interceptor must call commit() on the resolved strategy "
                        + "when the @Transactional method returns normally")
                .isGreaterThanOrEqualTo(1);
    }
}
