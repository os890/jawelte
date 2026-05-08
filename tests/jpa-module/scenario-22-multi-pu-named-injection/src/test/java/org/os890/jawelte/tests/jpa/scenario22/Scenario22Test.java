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
package org.os890.jawelte.tests.jpa.scenario22;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Two persistence units (testPU22a + testPU22b) bootstrap as separate synthetic
 * beans qualified {@code @Named(puName)}. Two {@code @Named}-qualified injection
 * points each route to the right PU's EMF / EM, and the EMF metamodels are
 * disjoint (entity classes are listed per-PU with
 * {@code <exclude-unlisted-classes>true</exclude-unlisted-classes>}).
 */
@EnableTestBeans
public class Scenario22Test {

    @Inject
    @Named("testPU22a")
    private EntityManagerFactory entityManagerFactoryA;

    @Inject
    @Named("testPU22b")
    private EntityManagerFactory entityManagerFactoryB;

    @Inject
    @Named("testPU22a")
    private EntityManager entityManagerA;

    @Inject
    @Named("testPU22b")
    private EntityManager entityManagerB;

    /** No-arg constructor for CDI. */
    public Scenario22Test() {
    }

    /** Each @Named injection routes to its own PU's bean — distinct identity, disjoint metamodels. */
    @Test
    public void namedQualifierRoutesToCorrectPu() {
        assertThat(entityManagerFactoryA)
                .as("the two @Named EMFs must be distinct instances")
                .isNotSameAs(entityManagerFactoryB);

        assertThat(entityManagerFactoryA.getMetamodel().getEntities())
                .as("PU 'a' must know MarkerA")
                .extracting(entityType -> entityType.getJavaType().getName())
                .containsExactly(MarkerA.class.getName());

        assertThat(entityManagerFactoryB.getMetamodel().getEntities())
                .as("PU 'b' must know MarkerB")
                .extracting(entityType -> entityType.getJavaType().getName())
                .containsExactly(MarkerB.class.getName());

        assertThat(entityManagerA)
                .as("the two @Named EMs must be distinct proxies")
                .isNotSameAs(entityManagerB);
    }
}
