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
package org.os890.jawelte.tests.testcontrol.scenario30;

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
import org.os890.jawelte.module.testcontrol.api.TestControl;

/**
 * Subject test class driven by {@link Scenario30Test} through
 * {@code EngineTestKit} — never run directly by surefire (it is not a
 * {@code *Test} class and method 1 always fails by design).
 *
 * <p>Method 1 is {@code @Transactional} and carries
 * {@code @TestControl(testData=…)}. {@code dbIn/account.xml} seeds
 * {@code (1, Alice)} but {@code dbExpected/account.xml} expects a
 * different name, so the transactional verify path — jpa-module's
 * {@code afterEach} → {@code AfterTestTransaction} → testcontrol's
 * observer → {@code DbDiff.assertEquals()} — raises an
 * {@link AssertionError}.
 *
 * <p>Method 2 carries NO {@code @TestControl}; it asserts the ACCOUNT
 * table is empty. It passes only if jpa-module truncated the table in
 * method 1's {@code afterEach} despite the verify failure — the contract
 * this scenario guards.
 */
@EnableTestBeans
@PersistenceConfig(persistenceUnitName = "testcontrolScenario30PU")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DbExpectedFailureSubject {

    @Inject
    private EntityManager entityManager;

    public DbExpectedFailureSubject() {
    }

    @Test
    @Order(1)
    @Transactional
    @TestControl(testData = "testdata/scenario30")
    void dbExpectedMismatchRaisesAssertionErrorOnTheTransactionalPath() {
        // No body work: dbIn seeds (1, Alice), dbExpected expects a
        // different name, so testcontrol's AfterTestTransaction verify
        // fails with an AssertionError after this method's transaction
        // commits.
    }

    @Test
    @Order(2)
    @Transactional
    void nextMethodMustSeeACleanTableDespiteThePreviousVerifyFailure() {
        long rows = entityManager
                .createQuery("SELECT COUNT(a) FROM Account a", Long.class)
                .getSingleResult();
        assertThat(rows)
                .as("jpa-module must still truncate the table in afterEach even when the "
                        + "dbExpected verify threw an AssertionError in the previous method")
                .isZero();
    }
}
