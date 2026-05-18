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
package org.os890.jawelte.tests.quarkus.scenario27;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;

class Scenario27Test {

    @Test
    void instanceFieldTestBeanFailsTheRunWithMustBeStaticMessage() {
        List<Event> failures = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario27Subject.class))
                .execute()
                .allEvents()
                .failed()
                .list();

        Optional<Throwable> firstFailure = failures.stream()
                .map(event -> event.getRequiredPayload(TestExecutionResult.class))
                .map(TestExecutionResult::getThrowable)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

        assertThat(firstFailure).isPresent();
        assertThat(rootCause(firstFailure.get())).hasMessageContaining("must be static");
    }

    private static Throwable rootCause(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
