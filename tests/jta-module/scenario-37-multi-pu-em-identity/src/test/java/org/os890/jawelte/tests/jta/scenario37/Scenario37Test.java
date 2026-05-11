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
package org.os890.jawelte.tests.jta.scenario37;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Multi-PU under JTA — the synthetic CDI beans for two distinct
 * persistence units must resolve to distinct {@link EntityManagerFactory}
 * and {@link EntityManager} proxies. Validates jpa-module's
 * {@code @Named} qualifier wiring under the JTA strategy + multi-PU
 * combination.
 */
@EnableTestBeans
public class Scenario37Test {

    @Inject
    @Named("testJtaPU37a")
    private EntityManagerFactory factoryA;

    @Inject
    @Named("testJtaPU37b")
    private EntityManagerFactory factoryB;

    @Inject
    @Named("testJtaPU37a")
    private EntityManager entityManagerA;

    @Inject
    @Named("testJtaPU37b")
    private EntityManager entityManagerB;

    /** No-arg constructor for CDI. */
    public Scenario37Test() {
    }

    @Test
    public void emfsAreDistinctPerPu() {
        assertThat(factoryA).isNotNull();
        assertThat(factoryB).isNotNull();
        assertThat(factoryA)
                .as("each PU must have its own EntityManagerFactory")
                .isNotSameAs(factoryB);
    }

    @Test
    public void emProxiesAreDistinctPerPu() {
        assertThat(entityManagerA).isNotNull();
        assertThat(entityManagerB).isNotNull();
        assertThat(entityManagerA)
                .as("each PU must have its own EntityManager proxy")
                .isNotSameAs(entityManagerB);
    }
}
