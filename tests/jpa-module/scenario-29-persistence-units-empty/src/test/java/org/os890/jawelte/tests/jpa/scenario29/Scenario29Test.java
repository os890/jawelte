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
package org.os890.jawelte.tests.jpa.scenario29;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;

/**
 * {@code @PersistenceConfig} with no {@code persistenceUnits} attribute set
 * (defaults to an empty array) means "all PUs in {@code persistence.xml}
 * bootstrap" — both {@code testPU29a} and {@code testPU29b} get registered.
 */
@EnableTestBeans
@PersistenceConfig
public class Scenario29Test {

    @Inject
    private Instance<EntityManagerFactory> entityManagerFactories;

    /** No-arg constructor for CDI. */
    public Scenario29Test() {
    }

    /** Empty filter → both PUs bootstrap as @Named synthetic beans. */
    @Test
    public void emptyPersistenceUnitsFilterBootstrapsAllDeclaredPus() {
        assertThat(entityManagerFactories.select(NamedLiteral.of("testPU29a")).isResolvable())
                .as("empty filter must bootstrap testPU29a")
                .isTrue();
        assertThat(entityManagerFactories.select(NamedLiteral.of("testPU29b")).isResolvable())
                .as("empty filter must bootstrap testPU29b")
                .isTrue();
    }
}
