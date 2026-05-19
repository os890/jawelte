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
package org.os890.jawelte.tests.testcontrol.scenario02;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;
import org.os890.jawelte.module.testcontrol.api.TestControl;


import io.quarkus.test.junit.QuarkusTest;
/**
 * Scenario 02 — transactional verify path. {@code dbIn/customers.xml}
 * seeds {@code (1, Alice)} and {@code (2, Bob)}. The test method
 * itself carries {@code @Transactional} so jpa-module's lifecycle
 * adapter opens a transaction in {@code beforeEach} and fires the
 * {@code AfterTestTransaction} event from its {@code afterEach}
 * after committing. testcontrol's
 * {@code TestDataHandler.onAfterTestTransaction} observer then runs
 * {@code DbDiff.assertEquals} against
 * {@code dbExpected/customers.xml} (Alice + Robert) on the
 * still-open managed connection. A diff mismatch raises
 * {@code AssertionError} out of the CDI event dispatch and fails the
 * test method. Reaching the end of the test method body without that
 * failure is what this scenario verifies.
 */
@EnableTestBeans
@QuarkusTest
@PersistenceConfig(persistenceUnitName = "testcontrolScenario02PU")
class Scenario02Test {

    @Inject
    private EntityManager entityManager;

    @Test
    @Transactional
    @TestControl(testData = "testdata/scenario02")
    void dbExpectedVerifiesPostCommitStateAfterTransactionalMethod() {
        entityManager
                .createNativeQuery("UPDATE CUSTOMER SET NAME = 'Robert' WHERE ID = 2")
                .executeUpdate();
        // No explicit assert: testcontrol's AfterTestTransaction
        // observer runs the dbExpected verification after this
        // method's transaction commits.
    }
}
