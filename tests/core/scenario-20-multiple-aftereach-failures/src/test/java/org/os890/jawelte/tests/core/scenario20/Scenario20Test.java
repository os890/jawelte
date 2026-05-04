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
package org.os890.jawelte.tests.core.scenario20;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineTestKit;

class Scenario20Test {

    @Test
    void firstAfterEachExceptionInLifoOrderIsPrimaryOthersSuppressed() {
        Throwable thrown = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario20Subject.class))
                .execute()
                .testEvents()
                .failed()
                .stream()
                .map(event -> event.getRequiredPayload(TestExecutionResult.class))
                .map(TestExecutionResult::getThrowable)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElseThrow();

        // afterEach LIFO order is 200 -> 100 -> 50, so the first throw
        // (which becomes primary) is from priority 200.
        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CCC afterEach failure from priority 200");

        // The other two are suppressed in iteration order.
        assertThat(Arrays.asList(thrown.getSuppressed()))
                .hasSize(2)
                .extracting(Throwable::getMessage)
                .containsExactly(
                        "BBB afterEach failure from priority 100",
                        "AAA afterEach failure from priority 50");
    }
}
