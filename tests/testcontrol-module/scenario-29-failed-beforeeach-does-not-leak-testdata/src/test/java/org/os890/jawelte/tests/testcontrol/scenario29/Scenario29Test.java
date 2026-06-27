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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

/**
 * Scenario 29 — a {@code @TestControl} method whose {@code beforeEach}
 * throws must NOT leak its {@code testData} annotation onto the
 * {@code @ApplicationScoped} {@code TestDataHandler} and corrupt a later
 * test method.
 *
 * <p>Drives {@link LeakProbeSubject} through {@code EngineTestKit}:
 * method 1 fails in testcontrol's {@code beforeEach} (missing test-data
 * folder); method 2 is an untagged {@code @Transactional} method whose
 * jpa-fired {@code AfterTestTransaction} would, on the leaked handler,
 * verify method 1's stale {@code testData}. After the fix the handler
 * publishes {@code activeAnnotation} only once seeding succeeds, so the
 * failed method 1 leaves nothing behind and method 2 succeeds.
 *
 * <p>Expected outcome: exactly one failure (method 1) and one success
 * (method 2). On the unfixed handler method 2 also fails, so the
 * statistics assertion (and the success-by-name check) catch the leak.
 */
class Scenario29Test {

    @Test
    void failedBeforeEachDoesNotLeakTestDataIntoTheNextMethod() {
        Events tests = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(LeakProbeSubject.class))
                .execute()
                .testEvents();

        tests.assertStatistics(stats -> stats.started(2).succeeded(1).failed(1));

        List<String> succeeded = tests.finished().stream()
                .filter(event -> event.getPayload(TestExecutionResult.class)
                        .map(result -> result.getStatus() == TestExecutionResult.Status.SUCCESSFUL)
                        .orElse(false))
                .map(event -> event.getTestDescriptor().getDisplayName())
                .toList();

        assertThat(succeeded)
                .as("the untagged @Transactional method must succeed; it must not "
                        + "inherit the failed method's leaked @TestControl(testData)")
                .containsExactly("untaggedMethodMustNotInheritLeakedTestData()");
    }
}
