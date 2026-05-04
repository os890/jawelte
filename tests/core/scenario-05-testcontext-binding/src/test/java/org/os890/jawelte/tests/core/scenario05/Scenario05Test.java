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
package org.os890.jawelte.tests.core.scenario05;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

class Scenario05Test {

    @Test
    void testContextGetTestClassReturnsTheSubjectAcrossAllCallbacks() {
        RecordingContainerPort.testClassesSeen.clear();

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario05Subject.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));

        // 5 callbacks (beforeAll, postProcessTestInstance, beforeEach, afterEach, afterAll)
        // - all should see the same TestContext.getTestClass(), namely Scenario05Subject.
        assertThat(RecordingContainerPort.testClassesSeen)
                .hasSize(5)
                .allMatch(c -> c.equals(Scenario05Subject.class));
    }
}
