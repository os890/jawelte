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
package org.os890.jawelte.tests.jpa.scenario01;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

import io.quarkus.test.junit.QuarkusTest;

/**
 * {@code @Inject EntityManager} resolves to a non-null transaction-scoped proxy that
 * can run JPQL. Two lookups in the same test method return the same proxy instance —
 * the contract POC's {@code JpaTestExtensionTest.emIsInjectableAndTransactionScoped}
 * locks in via {@code assertSame(em1, em2)}.
 */
@EnableTestBeans
@QuarkusTest
public class Scenario01Test {

    @Inject
    private EntityManager entityManager;

    @Inject
    private Instance<EntityManager> entityManagerInstance;

    /** No-arg constructor for CDI. */
    public Scenario01Test() {
    }

    /** The injected EntityManager is non-null and can run JPQL inside a tx. */
    @Test
    @Transactional
    public void entityManagerIsInjectableAndUsable() {
        assertThat(entityManager)
                .as("@Inject EntityManager must resolve to a non-null bean")
                .isNotNull();

        long count = entityManager
                .createQuery("SELECT COUNT(m) FROM Marker m", Long.class)
                .getSingleResult();
        assertThat(count)
                .as("a fresh PU should have zero rows in the Marker table")
                .isZero();
    }

    /** Two CDI lookups in one method return the same proxy — transaction-scoped contract. */
    @Test
    public void twoLookupsReturnSameProxy() {
        EntityManager firstLookup = entityManagerInstance.get();
        EntityManager secondLookup = entityManagerInstance.get();
        assertThat(firstLookup)
                .as("CDI must return the same EntityManager proxy on repeated lookups")
                .isSameAs(secondLookup);
    }
}
