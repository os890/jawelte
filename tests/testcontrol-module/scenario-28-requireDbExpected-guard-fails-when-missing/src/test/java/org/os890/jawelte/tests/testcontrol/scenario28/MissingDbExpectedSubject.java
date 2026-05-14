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
package org.os890.jawelte.tests.testcontrol.scenario28;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.testcontrol.api.TestControl;

/**
 * Subject test class driven by {@link Scenario08Test} through
 * {@code EngineTestKit}. Carries a {@code @TestControl(testData=…)}
 * referencing a folder that contains a {@code dbIn/} sub-directory
 * but no {@code dbExpected/} — the default
 * {@code requireDbExpected=true} guard fires during
 * testcontrol-module's {@code beforeEach}, failing the test method
 * with an {@code IllegalStateException}. {@link Scenario08Test}
 * asserts on that failure.
 *
 * <p>Not run directly by the surefire test suite (would always fail).
 * Discovered only by Scenario08Test's {@code EngineTestKit} launch.
 */
@EnableTestBeans
public class MissingDbExpectedSubject {

    public MissingDbExpectedSubject() {
    }

    @Test
    @TestControl(testData = "testdata/scenario28")
    void shouldFailBecauseDbExpectedIsAbsent() {
        // Never reached — testcontrol's beforeEach raises
        // IllegalStateException before this method body runs.
    }
}
