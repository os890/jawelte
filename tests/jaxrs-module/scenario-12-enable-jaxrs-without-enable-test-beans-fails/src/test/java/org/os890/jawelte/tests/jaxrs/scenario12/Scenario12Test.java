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
package org.os890.jawelte.tests.jaxrs.scenario12;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;

/**
 * Scenario 12 — drives the misconfigured {@link Scenario12Subject}
 * through {@code EngineTestKit} and inspects the resulting failure:
 * it must be an {@link IllegalStateException} whose message
 * contains {@code "@EnableJaxRs requires @EnableTestBeans"} so the
 * user gets an actionable pointer to the missing annotation.
 *
 * <p>The failure is published as a CONTAINER finish event (not a
 * TEST event) because the failure originates in {@code beforeAll}
 * — no test method ever runs.
 */
class Scenario12Test {

    @Test
    void enableJaxRsAloneFailsWithActionableMessage() {
        List<Event> containerFinished = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario12Subject.class))
                .execute()
                .containerEvents()
                .finished()
                .stream()
                .toList();

        Optional<Throwable> failure = containerFinished.stream()
                .map(event -> event.getPayload(TestExecutionResult.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(result -> result.getStatus() == TestExecutionResult.Status.FAILED)
                .findFirst()
                .flatMap(TestExecutionResult::getThrowable);

        assertThat(failure)
                .as("the misconfigured subject must surface a failure on its container event")
                .isPresent();

        Throwable cursor = failure.get();
        IllegalStateException guardFailure = null;
        while (cursor != null) {
            if (cursor instanceof IllegalStateException ise) {
                guardFailure = ise;
                break;
            }
            cursor = cursor.getCause();
        }
        assertThat(guardFailure)
                .as("Expected an IllegalStateException in the failure chain — got: " + failure.get())
                .isNotNull();
        assertThat(guardFailure.getMessage())
                .as("guard message must point at the missing @EnableTestBeans annotation")
                .contains("@EnableJaxRs requires @EnableTestBeans");
    }
}
