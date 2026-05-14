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
package org.os890.jawelte.tests.testcontrol.scenario08a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;
import org.junit.platform.testkit.engine.EventType;

/**
 * Scenario 08a — invalid {@code puName:} prefix. {@link UnknownPuNameSubject}
 * carries
 * {@code @TestControl(testData = "thisPersistenceUnitIsNotDeclared:testdata/scenario08a")},
 * but {@code thisPersistenceUnitIsNotDeclared} is NOT in
 * {@code META-INF/persistence.xml}. testcontrol's seed-transaction
 * template tries to look up an {@code EntityManager} CDI bean
 * qualified with {@code @Named("thisPersistenceUnitIsNotDeclared")} —
 * no such bean is registered, so CDI's unsatisfied-resolution path
 * raises an exception and propagates out of the
 * {@code @Transactional} interceptor, failing the test method.
 *
 * <p>The exact exception class depends on the CDI runtime
 * (OpenWebBeans vs Weld); the assertion is therefore on the failure
 * cause chain mentioning the bogus PU name, which makes the error
 * actionable: a user grepping the test output for the typo finds the
 * offending {@code puName:} prefix.
 */
class Scenario08aTest {

    @Test
    void unknownPuNamePrefixFailsTheTestMethod() {
        List<Event> finished = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(UnknownPuNameSubject.class))
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
                .as("Test must finish with a failure (unknown puName: prefix)")
                .isPresent();

        // Walk the cause chain collecting all messages, then assert
        // somewhere in the chain mentions the bogus PU name.
        StringBuilder allMessages = new StringBuilder();
        Throwable cursor = failure.get();
        while (cursor != null) {
            if (cursor.getMessage() != null) {
                allMessages.append(cursor.getClass().getSimpleName())
                        .append(": ")
                        .append(cursor.getMessage())
                        .append("\n");
            }
            cursor = cursor.getCause();
        }
        assertThat(allMessages.toString())
                .as("Failure cause chain must mention the bogus PU name to be actionable")
                .contains("thisPersistenceUnitIsNotDeclared");
    }
}
