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
package example.tableresolver;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;

/**
 * Demonstrates that the custom SkipCleanupTableNameResolver actually
 * wins the SPI selection: the first method writes a Note; the second
 * method sees the same Note instead of an empty table.
 */
@EnableTestBeans
@PersistenceConfig(persistenceUnitName = "notesPU")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SurvivingDataTest {

    @Inject
    EntityManager entityManager;

    @Test
    @Order(1)
    @Transactional
    void firstMethodWritesARow() {
        entityManager.persist(new Note("survives across methods"));
        Long count = entityManager.createQuery("SELECT COUNT(n) FROM Note n", Long.class).getSingleResult();
        assertThat(count).isEqualTo(1L);
    }

    @Test
    @Order(2)
    @Transactional
    void secondMethodStillSeesIt() {
        Long count = entityManager.createQuery("SELECT COUNT(n) FROM Note n", Long.class).getSingleResult();
        assertThat(count)
                .as("custom TableNameResolver returns no tables, so cleanup wiped nothing")
                .isEqualTo(1L);
    }
}
