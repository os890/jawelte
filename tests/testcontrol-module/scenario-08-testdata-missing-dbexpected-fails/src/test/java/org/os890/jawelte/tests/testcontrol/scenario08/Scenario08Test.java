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
package org.os890.jawelte.tests.testcontrol.scenario08;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;
import org.junit.platform.testkit.engine.EventType;

/**
 * Scenario 08 — guards the {@code requireDbExpected} default
 * behaviour. {@link MissingDbExpectedSubject} carries
 * {@code @TestControl(testData="testdata/scenario08")} where
 * {@code testdata/scenario08/} contains only a {@code dbIn/}
 * sub-folder. The default {@code requireDbExpected=true} therefore
 * detects "no {@code dbExpected/*.xml} contribution across the
 * entries" and raises {@link IllegalStateException} from
 * testcontrol's {@code beforeEach} — failing the test method before
 * its body runs.
 *
 * <p>This test class drives the subject through JUnit's
 * {@code EngineTestKit} and inspects the resulting failure: it must
 * be an {@link IllegalStateException} whose message mentions
 * {@code "requires at least one dbExpected"} so the user gets a
 * pointer to the configuration error.
 */
class Scenario08Test {

    @Test
    void requireDbExpectedGuardFailsWhenNoDbExpectedXmlAcrossEntries() {
        List<Event> finished = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(MissingDbExpectedSubject.class))
                .execute()
                .testEvents()
                .finished()
                .stream()
                .toList();

        assertThat(finished)
                .as("Expect exactly one test method finish event (the failing one)")
                .hasSize(1);

        Event finishedEvent = finished.get(0);
        assertThat(finishedEvent.getType()).isEqualTo(EventType.FINISHED);

        Optional<Throwable> failure = finishedEvent
                .getPayload(org.junit.platform.engine.TestExecutionResult.class)
                .flatMap(result -> result.getThrowable());

        assertThat(failure)
                .as("Test must finish with a failure (the guard's IllegalStateException)")
                .isPresent();

        Throwable thrown = failure.get();
        // The IllegalStateException is the root of the failure chain
        // — JUnit wraps lifecycle exceptions in its own org.opentest4j
        // types in some configurations, so walk the cause chain to
        // find the IllegalStateException with our message.
        Throwable cursor = thrown;
        IllegalStateException guardFailure = null;
        while (cursor != null) {
            if (cursor instanceof IllegalStateException illegalState) {
                guardFailure = illegalState;
                break;
            }
            cursor = cursor.getCause();
        }
        assertThat(guardFailure)
                .as("Expected an IllegalStateException in the failure chain — got: " + thrown)
                .isNotNull();
        assertThat(guardFailure.getMessage())
                .as("Guard error message must point at the missing dbExpected/")
                .contains("requires at least one dbExpected");
    }
}
