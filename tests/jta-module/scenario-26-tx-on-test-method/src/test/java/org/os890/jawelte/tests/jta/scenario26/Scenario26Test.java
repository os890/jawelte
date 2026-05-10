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
package org.os890.jawelte.tests.jta.scenario26;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.port.TransactionStrategy;

/**
 * Port of jpa-module scenario 09 — {@code @Transactional} on a JUnit
 * {@code @Test} method works under JTA. jpa-module's lifecycle adapter
 * sees the annotation in {@code beforeEach}, calls
 * {@code TransactionStrategy.begin()} (which under JTA dispatches to
 * {@code JtaTransactionStrategy} and {@code TM.begin()}), activates
 * {@code TransactionScopedContext}, lets JUnit invoke the body, and
 * commits / rolls back in {@code afterEach}.
 */
@EnableTestBeans
public class Scenario26Test {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public Scenario26Test() {
    }

    @Test
    @Transactional
    public void txIsActiveInsideTransactionalTestMethod() {
        TransactionStrategy strategy = TestContext.loadService(TransactionStrategy.class);
        assertThat(strategy.isActive())
                .as("the lifecycle adapter must have begun a JTA tx for the @Transactional @Test method")
                .isTrue();

        entityManager.persist(new Marker());
        entityManager.flush();

        long count = entityManager.createQuery("SELECT COUNT(m) FROM Marker m", Long.class)
                .getSingleResult();
        assertThat(count)
                .as("@Inject EntityManager must resolve to a working EM inside the @Transactional @Test body")
                .isEqualTo(1L);
    }
}
