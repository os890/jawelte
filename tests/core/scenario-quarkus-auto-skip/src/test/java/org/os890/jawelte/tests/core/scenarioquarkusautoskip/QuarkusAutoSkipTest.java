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
package org.os890.jawelte.tests.core.scenarioquarkusautoskip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

class QuarkusAutoSkipTest {

    @Test
    void quarkusAnnotatedClassSkipsBeforeAllAndAfterAllOnContainerPort() {
        TestScenarioRecordingContainerPort.resetCounters();

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(QuarkusSubject.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.succeeded(1));

        // @QuarkusTest must auto-disable container management. Quarkus
        // already runs the bean container; jawelte must NOT boot a
        // second one. The other lifecycle phases still fire so module
        // ports and per-test setup remain consistent with
        // manageContainer=false.
        assertThat(TestScenarioRecordingContainerPort.BEFORE_ALL_CALLS.get())
                .as("beforeAll must NOT be called for @QuarkusTest classes")
                .isZero();
        assertThat(TestScenarioRecordingContainerPort.AFTER_ALL_CALLS.get())
                .as("afterAll must NOT be called for @QuarkusTest classes")
                .isZero();

        assertThat(TestScenarioRecordingContainerPort.POST_PROCESS_CALLS.get())
                .as("postProcessTestInstance still fires under @QuarkusTest")
                .isEqualTo(1);
        assertThat(TestScenarioRecordingContainerPort.BEFORE_EACH_CALLS.get())
                .as("beforeEach still fires under @QuarkusTest")
                .isEqualTo(1);
        assertThat(TestScenarioRecordingContainerPort.AFTER_EACH_CALLS.get())
                .as("afterEach still fires under @QuarkusTest")
                .isEqualTo(1);
    }

    @Test
    void plainEnableTestBeansClassStillBootsThroughContainerPort() {
        TestScenarioRecordingContainerPort.resetCounters();

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(PlainSubject.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.succeeded(1));

        // Sanity check: a plain @EnableTestBeans class without
        // @QuarkusTest still goes through the full lifecycle. This
        // proves the recording port itself is wired correctly via
        // ServiceLoader and that the @QuarkusTest case above is
        // genuinely opting out, not silently broken.
        assertThat(TestScenarioRecordingContainerPort.BEFORE_ALL_CALLS.get()).isEqualTo(1);
        assertThat(TestScenarioRecordingContainerPort.AFTER_ALL_CALLS.get()).isEqualTo(1);
        assertThat(TestScenarioRecordingContainerPort.POST_PROCESS_CALLS.get()).isEqualTo(1);
        assertThat(TestScenarioRecordingContainerPort.BEFORE_EACH_CALLS.get()).isEqualTo(1);
        assertThat(TestScenarioRecordingContainerPort.AFTER_EACH_CALLS.get()).isEqualTo(1);
    }
}
