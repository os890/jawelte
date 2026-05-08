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
package org.os890.jawelte.tests.jpa.scenario06;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * The persistence unit declares no {@code <class>} elements but keeps
 * {@code <exclude-unlisted-classes>false</exclude-unlisted-classes>}. jpa-module's
 * {@code EntityScanner} (xbean-finder) walks the test classpath and registers every
 * {@code @Entity} type — verified end-to-end here via the EMF metamodel + a real
 * persist/query roundtrip.
 */
@EnableTestBeans
public class Scenario06Test {

    @Inject
    private EntityManagerFactory entityManagerFactory;

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public Scenario06Test() {
    }

    /** Auto-discovered entity reaches the EMF metamodel. */
    @Test
    public void scannerRegistersTheEntityWithEmfMetamodel() {
        assertThat(entityManagerFactory.getMetamodel().getEntities())
                .as("xbean-finder scan must register AutoDiscoveredEntity even though "
                        + "persistence.xml has no <class> element for it")
                .extracting(entityType -> entityType.getJavaType().getName())
                .contains(AutoDiscoveredEntity.class.getName());
    }

    /** Persist + query proves the schema was generated and the EM can drive it. */
    @Test
    @Transactional
    public void persistAndQueryAutoDiscoveredEntity() {
        entityManager.persist(new AutoDiscoveredEntity("scenario-06"));
        entityManager.flush();

        long count = entityManager
                .createQuery("SELECT COUNT(e) FROM AutoDiscoveredEntity e", Long.class)
                .getSingleResult();
        assertThat(count)
                .as("auto-discovered entity must be queryable via JPQL after schema generation")
                .isEqualTo(1L);
    }
}
