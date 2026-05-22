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
package example.seedonly;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;
import org.os890.jawelte.module.testcontrol.api.TestControl;

/**
 * Demonstrates the opt-out for testcontrol-module's default
 * "must supply dbExpected" guard. The {@code testdata/products}
 * folder under {@code src/test/resources} contains only a {@code dbIn/}
 * sub-folder — no {@code dbExpected/}. By default, that would fail the
 * test method in {@code beforeEach} with
 * {@code IllegalStateException: ... requires at least one dbExpected
 * ...} (the guard against silent regressions where the verify side
 * gets accidentally deleted).
 *
 * <p>{@code requireDbExpected = false} on the {@code @TestControl}
 * disables the guard for this method, allowing legitimate fixture-only
 * tests — e.g. a row a downstream test relies on, or a smoke test that
 * verifies the seeded state via direct {@code EntityManager} queries
 * instead of {@code DbDiff}.
 */
@EnableTestBeans
@PersistenceConfig(persistenceUnitName = "productsPU")
class SeedOnlyTest {

    @Inject
    EntityManager entityManager;

    @Test
    @Transactional
    @TestControl(testData = "testdata/products", requireDbExpected = false)
    void dbInSeedsButNoDbExpectedNeededWhenGuardIsOff() {
        Long count = entityManager
                .createQuery("SELECT COUNT(p) FROM Product p", Long.class)
                .getSingleResult();
        assertThat(count)
                .as("dbIn/products.xml clean-inserted two rows; the test verifies via EntityManager, not DbDiff")
                .isEqualTo(2L);
    }
}
