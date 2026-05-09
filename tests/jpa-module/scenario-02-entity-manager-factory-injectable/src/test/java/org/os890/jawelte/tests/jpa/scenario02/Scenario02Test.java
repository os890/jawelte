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
package org.os890.jawelte.tests.jpa.scenario02;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * {@code @Inject EntityManagerFactory} resolves to the JVM-cached EMF for the
 * scenario's PU; it is open and can create fresh EntityManagers.
 */
@EnableTestBeans
public class Scenario02Test {

    @Inject
    private EntityManagerFactory entityManagerFactory;

    /** No-arg constructor for CDI. */
    public Scenario02Test() {
    }

    /** The injected EMF is non-null, open, and can create EMs. */
    @Test
    public void entityManagerFactoryIsInjectableOpenAndUsable() {
        assertThat(entityManagerFactory)
                .as("@Inject EntityManagerFactory must resolve to a non-null bean")
                .isNotNull();
        assertThat(entityManagerFactory.isOpen())
                .as("the framework-managed EMF must be open across the test class")
                .isTrue();

        EntityManager freshEntityManager = entityManagerFactory.createEntityManager();
        try {
            assertThat(freshEntityManager.isOpen())
                    .as("EMF.createEntityManager() must return an open EntityManager")
                    .isTrue();
        } finally {
            freshEntityManager.close();
        }
    }

    /** The Marker entity is registered with the EMF metamodel — proves entity scan ran. */
    @Test
    public void emfMetamodelKnowsTheEntity() {
        assertThat(entityManagerFactory.getMetamodel().getEntities())
                .as("the EMF's metamodel must include the auto-discovered Marker entity")
                .extracting(entityType -> entityType.getJavaType().getName())
                .contains(Marker.class.getName());
    }
}
