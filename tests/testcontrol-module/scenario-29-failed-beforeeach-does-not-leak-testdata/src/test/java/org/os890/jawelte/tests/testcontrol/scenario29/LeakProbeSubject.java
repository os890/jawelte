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
package org.os890.jawelte.tests.testcontrol.scenario29;

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
 * Subject test class driven by {@link Scenario29Test} through
 * {@code EngineTestKit} — never run directly by surefire (it is not a
 * {@code *Test} class and method 1 always fails by design).
 *
 * <p>Method 1 carries {@code @TestControl(testData=…)} pointing at a
 * folder that is NOT on the classpath, so testcontrol's
 * {@code beforeEach} throws from {@code validateBaseFolders} before the
 * test body runs. Because the lifecycle extension records a port as
 * "completed" only after its {@code beforeEach} returns, a throwing
 * {@code beforeEach} means testcontrol's {@code afterEach} — and hence
 * {@code TestDataHandler.clearActive()} — never runs for method 1.
 *
 * <p>Method 2 carries NO {@code @TestControl}; it is a plain
 * {@code @Transactional} method. jpa-module fires
 * {@code AfterTestTransaction} unconditionally in its {@code afterEach},
 * which drives {@code TestDataHandler.verifyAll()}. If method 1's
 * annotation leaked onto the {@code @ApplicationScoped} handler, method 2
 * would verify against method 1's (missing) test-data folder and fail
 * with a spurious error it never asked for. Method 2 must therefore
 * succeed — that is what {@link Scenario29Test} asserts.
 */
@EnableTestBeans
@PersistenceConfig(persistenceUnitName = "testcontrolScenario29PU")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LeakProbeSubject {

    @Inject
    private EntityManager entityManager;

    public LeakProbeSubject() {
    }

    @Test
    @Order(1)
    @TestControl(testData = "testdata/scenario29-folder-that-does-not-exist")
    void failsInBeforeEachBecauseTestDataFolderIsMissing() {
        // Never reached — testcontrol's beforeEach raises
        // IllegalArgumentException ("Test data folder not found") before
        // this body runs. This is the trigger for the leak the sibling
        // method must NOT inherit.
    }

    @Test
    @Order(2)
    @Transactional
    void untaggedMethodMustNotInheritLeakedTestData() {
        // Plain @Transactional method with no @TestControl. jpa-module
        // still fires AfterTestTransaction in afterEach; the handler must
        // see a cleared activeAnnotation and skip verification.
        entityManager
                .createQuery("SELECT COUNT(p) FROM Probe p", Long.class)
                .getSingleResult();
    }
}
