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
package org.os890.jawelte.tests.core.scenario22;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

class Scenario22Test {

    @Test
    void containerAfterAllRunsEvenWhenContainerBeforeAllThrew() {
        BeforeAllThrowingContainerPort.AFTER_ALL_CALLED.set(false);
        RecordingModulePort.BEFORE_ALL_CALLED.set(false);

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario22Subject.class))
                .execute();

        // No module lifecycle port's beforeAll ran (the container port's
        // beforeAll threw, so the delegating extension propagated and
        // never reached the loop over module ports).
        assertThat(RecordingModulePort.BEFORE_ALL_CALLED.get())
                .as("RecordingModulePort.beforeAll must NOT be called when "
                        + "containerPort.beforeAll throws")
                .isFalse();

        // The container port's afterAll IS still called - cleanup
        // guarantee for partial state.
        assertThat(BeforeAllThrowingContainerPort.AFTER_ALL_CALLED.get())
                .as("BeforeAllThrowingContainerPort.afterAll must be called "
                        + "even when its beforeAll threw")
                .isTrue();
    }
}
