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
package example.seedmodes;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.dbtestdata.api.DbSeed;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;

/**
 * SeedMode demonstrations:
 *
 *   .cleanInsert() — DELETE all + INSERT (the default; covered in listing 01)
 *   .insert()      — INSERT only; duplicate PK throws
 *   .update()      — UPDATE existing rows; missing PK throws
 *   .refresh()     — Upsert (INSERT when absent, UPDATE when present)
 *
 * This listing chains cleanInsert (initial=2 rows) then refresh (id=2
 * gets renamed, id=3 is inserted) and verifies the final state.
 */
@EnableTestBeans
@PersistenceConfig(persistenceUnitName = "productsPU")
class SeedModesTest {

    @Inject
    EntityManager entityManager;

    @Test
    @Transactional
    void cleanInsertThenRefreshUpserts() {
        DbSeed.forPersistenceUnit().dataset("products/initial.xml").cleanInsert().execute();
        DbSeed.forPersistenceUnit().dataset("products/refresh.xml").refresh().execute();

        // refresh: id=1 untouched, id=2 renamed, id=3 inserted -> 3 rows total.
        Long count = entityManager.createQuery("SELECT COUNT(p) FROM Product p", Long.class).getSingleResult();
        assertThat(count).isEqualTo(3L);

        // Spot-check id=2 has the refreshed name.
        Object name = entityManager.createNativeQuery("SELECT NAME FROM PRODUCT WHERE ID=2").getSingleResult();
        assertThat(name).isEqualTo("banana-fresh");
    }
}
