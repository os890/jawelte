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
package org.os890.jawelte.tests.jpa.scenario21;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;


import io.quarkus.test.junit.QuarkusTest;
/**
 * With a single persistence unit declared, jpa-module's {@code JpaCdiExtension}
 * registers the synthetic EMF + EM beans as {@code @Default} so an unqualified
 * {@code @Inject EntityManager} resolves to that PU's bean. The explicit
 * {@code @Default} qualifier resolves to the same bean.
 */
@EnableTestBeans
@QuarkusTest
public class Scenario21Test {

    @Inject
    private EntityManager unqualifiedEntityManager;

    @Inject
    @Default
    private EntityManager defaultEntityManager;

    @Inject
    @Default
    private EntityManagerFactory defaultEntityManagerFactory;

    /** No-arg constructor for CDI. */
    public Scenario21Test() {
    }

    /** Unqualified inject + @Default-qualified inject both resolve in the single-PU shape. */
    @Test
    public void singlePuResolvesUnqualifiedAndDefaultInjections() {
        assertThat(unqualifiedEntityManager)
                .as("unqualified @Inject EntityManager must resolve in the single-PU shape")
                .isNotNull();
        assertThat(defaultEntityManager)
                .as("@Default EntityManager must resolve to the same proxy as unqualified")
                .isSameAs(unqualifiedEntityManager);
        assertThat(defaultEntityManagerFactory)
                .as("@Default EntityManagerFactory must resolve in the single-PU shape")
                .isNotNull();
        assertThat(defaultEntityManagerFactory.isOpen())
                .as("the @Default EMF must be open across the test class")
                .isTrue();
    }
}
