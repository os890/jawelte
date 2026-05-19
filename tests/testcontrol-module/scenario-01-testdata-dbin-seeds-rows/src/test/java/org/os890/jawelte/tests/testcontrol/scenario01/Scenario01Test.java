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
package org.os890.jawelte.tests.testcontrol.scenario01;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;
import org.os890.jawelte.module.testcontrol.api.TestControl;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Scenario 01 — happy-path test of the {@code @TestControl(testData)}
 * seeding pipeline. {@code testdata/scenario01/dbIn/customers.xml}
 * contains two CUSTOMER rows; testcontrol's lifecycle adapter calls
 * {@code TestDataHandler.seedAll} in {@code beforeEach}, which runs
 * the DbSeed inside a short-lived transaction owned by
 * {@code TestDataSeedTransactionTemplate}. By the time the test
 * method enters, the seed transaction has committed and the
 * CUSTOMER table holds the two seeded rows. The
 * {@code @Transactional} service injected here counts them.
 */
@EnableTestBeans
@QuarkusTest
@PersistenceConfig(persistenceUnitName = "testcontrolScenario01PU")
class Scenario01Test {

    @Inject
    private Scenario01CustomerCountService customerCountService;

    @Test
    @TestControl(testData = "testdata/scenario01", requireDbExpected = false)
    void dbInSeedsCustomerRowsBeforeTestMethod() {
        assertThat(customerCountService.countCustomers())
                .as("dbIn/customers.xml seeded 2 CUSTOMER rows by testcontrol's beforeEach")
                .isEqualTo(2);
    }
}
