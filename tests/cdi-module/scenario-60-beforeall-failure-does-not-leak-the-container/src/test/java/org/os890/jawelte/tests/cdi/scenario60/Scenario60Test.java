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
package org.os890.jawelte.tests.cdi.scenario60;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * One broken test class should cost one failure.
 *
 * <p>When a class fails in {@code beforeAll} after the CDI container
 * has started, the container has to be shut down on the way out. If it
 * is not, the next class in the same JVM fails with OpenWebBeans'
 * {@code ... is already registered} — and so does the one after that.
 * A run then reports N failures of which N−1 are noise, and which
 * class holds the real error moves with execution order.
 */
class Scenario60Test {

    @Test
    void aLaterClassIsEitherCleanOrToldWhereTheRealFailureIs() {
        EngineExecutionResults failing = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(FailingSubject.class))
                .execute();
        failing.containerEvents().assertStatistics(stats -> stats.failed(1));

        EngineExecutionResults healthy = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(HealthySubject.class))
                .execute();

        List<Throwable> collateral = healthy.containerEvents().failed().stream()
                .map(event -> event.getPayload(TestExecutionResult.class)
                        .flatMap(TestExecutionResult::getThrowable)
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();

        if (collateral.isEmpty()) {
            // The runtime exposed its container through CDI.current(),
            // so the failed bootstrap was closed and this class starts
            // on its own merits — the outcome worth having.
            healthy.testEvents().assertStatistics(stats -> stats.started(1).succeeded(1));
            return;
        }

        // Otherwise the container could not be released with portable
        // API, and the least this must do is say so: the error names
        // the class that actually failed, instead of leaving the reader
        // to decode the runtime's "already registered".
        assertThat(collateral)
                .as("a collateral failure has to identify the class holding the real error")
                .anySatisfy(failure -> assertThat(describe(failure))
                        .contains(FailingSubject.class.getName())
                        .contains("is the real one"));
    }

    private static String describe(Throwable failure) {
        StringBuilder text = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            text.append(current).append(' ');
        }
        return text.toString();
    }
}
