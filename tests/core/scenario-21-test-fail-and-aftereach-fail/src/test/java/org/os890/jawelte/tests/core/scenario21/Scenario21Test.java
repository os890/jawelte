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
package org.os890.jawelte.tests.core.scenario21;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineTestKit;

class Scenario21Test {

    @Test
    void testFailureIsPrimaryAndAfterEachFailureIsSuppressed() {
        Throwable thrown = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario21Subject.class))
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

        assertThat(thrown)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("subject test failure marker");

        assertThat(Arrays.asList(thrown.getSuppressed()))
                .extracting(Throwable::getMessage)
                .anyMatch(m -> m != null && m.contains("container afterEach failure marker"));
    }
}
