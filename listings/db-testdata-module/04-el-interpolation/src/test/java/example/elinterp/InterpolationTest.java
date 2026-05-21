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
package example.elinterp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.dbtestdata.api.DbSeed;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;

/**
 * dbIn.xml contains ${customerId} and ${customerName} placeholders.
 * DbSeed.withValues(Map.of(...)) feeds them to the EL interpolator
 * before the dataset hits the database — each test method can seed
 * variant data without producing a new XML file.
 */
@EnableTestBeans
@PersistenceConfig(persistenceUnitName = "customersPU")
class InterpolationTest {

    @Inject
    EntityManager entityManager;

    @Test
    @Transactional
    void elPlaceholdersAreResolvedAtSeedTime() {
        DbSeed.forPersistenceUnit()
                .dataset("customers/dbIn.xml")
                .withValues(Map.of("customerId", 42, "customerName", "Alice"))
                .cleanInsert()
                .execute();

        Object name = entityManager.createNativeQuery("SELECT NAME FROM CUSTOMER WHERE ID=42").getSingleResult();
        assertThat(name).isEqualTo("Alice");
    }
}
