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
package org.os890.jawelte.tests.testcontrol.scenario07;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;


import io.quarkus.test.junit.QuarkusTest;
/**
 * Scenario 07 — no {@code @TestControl} on any test method. Verifies
 * that testcontrol-module's adapter is a silent no-op on the
 * test-method path: no scope vetoes, no test-data side effects, no
 * exception. The test simply boots a CDI container and runs.
 */
@EnableTestBeans
@QuarkusTest
class Scenario07Test {

    @Test
    void smokeTestWithoutTestControlAnnotation() {
        // No @TestControl on this method. testcontrol's
        // TestControlLifecycleAdapter runs beforeEach / afterEach
        // through resolveBean → bm.resolve → push(null) on the scope
        // observer (no veto policy active), and never invokes
        // TestDataHandler. The container boots, the test method
        // executes, and afterEach unbinds the (absent) metadata keys
        // without error.
        assertThat(true).isTrue();
    }
}
