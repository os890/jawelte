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
package org.os890.jawelte.tests.jpa.scenario28;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;

/**
 * {@code @PersistenceConfig(persistenceUnits = {"testPU28a", "testPU28b"})}
 * restricts jpa-module's bootstrap to two of the three PUs declared in
 * {@code persistence.xml}. The two filtered-in PUs each get their
 * {@code @Named} synthetic EMF; the third is absent.
 */
@EnableTestBeans
@PersistenceConfig(persistenceUnits = {"testPU28a", "testPU28b"})
public class Scenario28Test {

    @Inject
    private Instance<EntityManagerFactory> entityManagerFactories;

    /** No-arg constructor for CDI. */
    public Scenario28Test() {
    }

    /** Filtered-in PUs resolve; the third PU's @Named EMF is absent. */
    @Test
    public void persistenceUnitsFilterRestrictsBootstrapToTheNamedPus() {
        Instance<EntityManagerFactory> filteredInA =
                entityManagerFactories.select(NamedLiteral.of("testPU28a"));
        Instance<EntityManagerFactory> filteredInB =
                entityManagerFactories.select(NamedLiteral.of("testPU28b"));
        Instance<EntityManagerFactory> filteredOutC =
                entityManagerFactories.select(NamedLiteral.of("testPU28c"));

        assertThat(filteredInA.isResolvable())
                .as("@Named(\"testPU28a\") EMF must be registered — it is on the filter list")
                .isTrue();
        assertThat(filteredInB.isResolvable())
                .as("@Named(\"testPU28b\") EMF must be registered — it is on the filter list")
                .isTrue();
        assertThat(filteredOutC.isUnsatisfied())
                .as("@Named(\"testPU28c\") EMF must be absent — filter excluded it")
                .isTrue();
    }
}
