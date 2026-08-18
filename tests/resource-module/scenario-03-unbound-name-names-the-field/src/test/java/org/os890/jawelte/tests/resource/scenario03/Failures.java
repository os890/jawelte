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
package org.os890.jawelte.tests.resource.scenario03;

import java.util.List;
import java.util.Objects;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineExecutionResults;

/**
 * Collects every throwable a subject run produced, whichever kind of
 * event carried it.
 *
 * <p>Where a bean-creation failure surfaces is a runtime's choice: it
 * can fail the class (a container event) or the test whose instance was
 * being injected (a test event). The scenario is about the message, not
 * about which of the two the runtime picked, so both are read.
 */
abstract class Failures {

    protected Failures() {
    }

    static List<Throwable> of(EngineExecutionResults results) {
        return java.util.stream.Stream.concat(
                        results.containerEvents().failed().stream(),
                        results.testEvents().failed().stream())
                .map(event -> event.getPayload(TestExecutionResult.class)
                        .flatMap(TestExecutionResult::getThrowable)
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * The whole causal chain as one string, because the runtime wraps
     * the failure in its own deployment exception and the message worth
     * asserting on is somewhere inside.
     */
    static String messageChain(Throwable throwable) {
        StringBuilder chain = new StringBuilder();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            chain.append(current).append(System.lineSeparator());
            if (current.getCause() == current) {
                break;
            }
        }
        return chain.toString();
    }
}
