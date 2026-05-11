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
package org.os890.jawelte.tests.jta.scenario40;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * JPA-style {@code @PersistenceContext(unitName=...)} and
 * {@code @PersistenceUnit(unitName=...)} annotations route to the
 * correct per-PU EntityManager / EntityManagerFactory under JTA +
 * multi-PU. {@code JpaCdiExtension}'s PAT rewriting must produce
 * different proxies for each {@code unitName}.
 */
@EnableTestBeans
public class Scenario40Test {

    @Inject
    private MultiPuAnnotationBean bean;

    /** No-arg constructor for CDI. */
    public Scenario40Test() {
    }

    @Test
    public void persistenceContextUnitNameRoutesToCorrectPu() {
        assertThat(bean.getEntityManagerA()).isNotNull();
        assertThat(bean.getEntityManagerB()).isNotNull();
        assertThat(bean.getEntityManagerA())
                .as("@PersistenceContext(unitName) must produce different EM proxies per PU")
                .isNotSameAs(bean.getEntityManagerB());
    }

    @Test
    public void persistenceUnitUnitNameRoutesToCorrectPu() {
        assertThat(bean.getFactoryA()).isNotNull();
        assertThat(bean.getFactoryB()).isNotNull();
        assertThat(bean.getFactoryA())
                .as("@PersistenceUnit(unitName) must produce different EMFs per PU")
                .isNotSameAs(bean.getFactoryB());
    }
}
