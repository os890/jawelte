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
package org.os890.jawelte.tests.core.scenario15;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;

class Scenario15Test {

    @Test
    void completedPortGetsAfterEachEvenWhenLaterPortThrows() {
        RecordedEvents.ENTRIES.clear();

        List<Event> failed = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario15Subject.class))
                .execute()
                .testEvents()
                .failed()
                .list();

        assertThat(failed).isNotEmpty();

        Throwable thrown = failed.stream()
                .map(event -> event.getRequiredPayload(TestExecutionResult.class))
                .map(TestExecutionResult::getThrowable)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElseThrow();

        // The beforeEach failure of beta propagated up to JUnit.
        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BBB beta beforeEach failure marker");

        // Cleanup guarantee: alpha.beforeEach completed, so alpha.afterEach
        // ran. beta.beforeEach threw, so beta.afterEach was NOT called.
        assertThat(RecordedEvents.ENTRIES).containsExactly(
                "alpha.beforeEach",
                "beta.beforeEach",
                "alpha.afterEach");
    }
}
