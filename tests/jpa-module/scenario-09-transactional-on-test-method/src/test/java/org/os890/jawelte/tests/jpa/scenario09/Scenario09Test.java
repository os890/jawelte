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
package org.os890.jawelte.tests.jpa.scenario09;

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
 * {@code @Transactional} on a JUnit {@code @Test} method:
 * jpa-module's lifecycle adapter detects the annotation in
 * {@code beforeEach}, calls {@code TransactionStrategy.begin()},
 * activates {@code TransactionScopedContext}, lets JUnit invoke
 * the body (during which {@code @Inject EntityManager} resolves
 * normally and {@code strategy.isActive()} returns {@code true}),
 * then commits or rolls back in {@code afterEach} based on the
 * test outcome.
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Scenario09Test {

    @Inject
    private EntityManager entityManager;

    @Inject
    private TxCommitObserver txCommitObserver;

    /** No-arg constructor for CDI. */
    public Scenario09Test() {
    }

    /** A @Transactional @Test sees an active tx and a working EntityManager. */
    @Test
    @Order(1)
    @Transactional
    public void txIsActiveInsideTransactionalTestMethod() {
        TransactionStrategy strategy = TestContext.loadService(TransactionStrategy.class);
        assertThat(strategy.isActive())
                .as("the lifecycle adapter must have begun a tx for the @Transactional @Test method")
                .isTrue();

        entityManager.persist(new Marker("scenario-09"));
        entityManager.flush();

        long count = entityManager.createQuery("SELECT COUNT(m) FROM Marker m", Long.class).getSingleResult();
        assertThat(count)
                .as("@Inject EntityManager must resolve to a working EM inside the @Test body")
                .isEqualTo(1L);
    }

    /**
     * The first method's commit fired (observable via the
     * TransactionCommitted event recorder); per-method cleanup
     * then wiped the markers.
     */
    @Test
    @Order(2)
    @Transactional
    public void firstTransactionalTestMethodCommitted() {
        assertThat(txCommitObserver.committedCount())
                .as("scenario-09's first method must have committed before scenario-09's second method ran")
                .isGreaterThanOrEqualTo(1);

        long count = entityManager.createQuery("SELECT COUNT(m) FROM Marker m", Long.class).getSingleResult();
        assertThat(count)
                .as("per-method cleanup wipes the table between @Transactional @Test methods")
                .isZero();
    }
}
