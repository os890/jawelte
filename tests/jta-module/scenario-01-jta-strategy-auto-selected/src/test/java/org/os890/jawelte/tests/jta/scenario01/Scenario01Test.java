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
package org.os890.jawelte.tests.jta.scenario01;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.PersistenceUnitTransactionType;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.port.TransactionStrategy;
import org.os890.jawelte.module.jta.impl.JtaTransactionStrategy;

/**
 * Ticket-006 scenario #01 — JTA strategy auto-selected. With
 * {@code jta-module} on the classpath, the project-wide
 * {@code ServicePriorityResolver} picks
 * {@link JtaTransactionStrategy} (at {@code @Priority(MAX_VALUE - 100)})
 * over jpa-module's
 * {@code DefaultResourceLocalTransactionStrategy} (at
 * {@code @Priority(MAX_VALUE)}). The strategy reports
 * {@link PersistenceUnitTransactionType#JTA} on
 * {@link TransactionStrategy#getTransactionType()}.
 */
@EnableTestBeans
public class Scenario01Test {

    /** No-arg constructor for CDI. */
    public Scenario01Test() {
    }

    @Test
    public void jtaStrategyWinsOverResourceLocalDefault() {
        TransactionStrategy strategy = TestContext.loadService(TransactionStrategy.class);
        assertThat(strategy)
                .as("With jta-module on the classpath, the active strategy must be JtaTransactionStrategy")
                .isInstanceOf(JtaTransactionStrategy.class);
        assertThat(strategy.getTransactionType())
                .as("The active strategy must report JTA")
                .isEqualTo(PersistenceUnitTransactionType.JTA);
    }
}
