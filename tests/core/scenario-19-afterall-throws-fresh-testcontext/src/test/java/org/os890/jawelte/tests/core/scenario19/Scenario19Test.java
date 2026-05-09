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
package org.os890.jawelte.tests.core.scenario19;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.os890.jawelte.core.api.port.TestContext;

class Scenario19Test {

    @Test
    void afterAllFailureDoesNotLeakTestContextToNextTestClass() {
        TestScenarioRecordingContainerPort.CONTEXTS_SEEN_IN_BEFORE_ALL.clear();

        EngineTestKit.engine("junit-jupiter")
                .selectors(
                        selectClass(FirstSubject.class),
                        selectClass(SecondSubject.class))
                .execute();

        assertThat(TestScenarioRecordingContainerPort.CONTEXTS_SEEN_IN_BEFORE_ALL).hasSize(2);
        TestContext firstContext = TestScenarioRecordingContainerPort.CONTEXTS_SEEN_IN_BEFORE_ALL.get(0);
        TestContext secondContext = TestScenarioRecordingContainerPort.CONTEXTS_SEEN_IN_BEFORE_ALL.get(1);

        // Each test class gets its own TestContext - the JUnit Store entry
        // disposed when the first class-level ExtensionContext closed
        // (even though afterAll threw), so the second class-level Store
        // bound a fresh TestContext.
        assertThat(firstContext).isNotSameAs(secondContext);
        assertThat(firstContext.getTestClass()).isEqualTo(FirstSubject.class);
        assertThat(secondContext.getTestClass()).isEqualTo(SecondSubject.class);
    }
}
