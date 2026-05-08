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
package org.os890.jawelte.tests.jpa.scenario41;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * A {@code @TestMethodScoped} bean's {@code @PreDestroy} (driven by
 * scope-module's {@code afterEach}) runs a JPQL query through a fresh
 * {@link EntityManager} created from the JVM-cached
 * {@link jakarta.persistence.EntityManagerFactory}. Two ordered methods:
 * method 1 persists a marker + materialises the bean; method 2 inspects
 * the static result captured by the bean's {@code @PreDestroy} (which fired
 * in the afterEach between the two methods).
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Scenario41Test {

    @Inject
    private EntityManager entityManager;

    @Inject
    private PreDestroyDbReader preDestroyDbReader;

    /** No-arg constructor for CDI. */
    public Scenario41Test() {
    }

    /** Method 1: persist a marker, materialise the bean — its @PreDestroy will fire after this method. */
    @Test
    @Order(1)
    @Transactional
    public void method1PersistsAndMaterialisesTheBean() {
        PreDestroyDbReader.reset();

        entityManager.persist(new Marker());
        entityManager.flush();
        preDestroyDbReader.touch();

        long inTxCount = entityManager
                .createQuery("SELECT COUNT(m) FROM Marker m", Long.class)
                .getSingleResult();
        assertThat(inTxCount)
                .as("the @Transactional method must see the row it just persisted")
                .isEqualTo(1L);
    }

    /** Method 2: assert that the previous method's @PreDestroy succeeded. */
    @Test
    @Order(2)
    public void method2VerifiesPreDestroyRanAndQueried() {
        assertThat(PreDestroyDbReader.FAILURE_AT_PREDESTROY.get())
                .as("the @TestMethodScoped @PreDestroy must execute its JPQL query without error")
                .isNull();
        assertThat(PreDestroyDbReader.COUNT_AT_PREDESTROY.get())
                .as("@PreDestroy ran a SELECT COUNT and got a non-null result — the EMF was still "
                        + "open and the schema was reachable")
                .isNotNull();
    }
}
