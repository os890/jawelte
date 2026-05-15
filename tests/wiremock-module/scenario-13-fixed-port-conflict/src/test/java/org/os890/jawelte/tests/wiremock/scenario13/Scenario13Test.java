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
package org.os890.jawelte.tests.wiremock.scenario13;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.net.BindException;
import java.net.ServerSocket;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Scenario 13 — fixed-port conflict. Pre-binds a
 * {@link ServerSocket} on {@link Scenario13Constants#SQUATTED_PORT},
 * then runs {@link Scenario13Subject} (which carries
 * {@code @WireMockEndpoint(port=SQUATTED_PORT)}) via
 * {@code EngineTestKit}. Asserts the deployment surfaces a
 * failure whose throwable chain mentions
 * {@link BindException} — confirms the WireMock start hit the
 * port conflict and raised the expected exception.
 *
 * <p>{@code try-with-resources} on the {@code ServerSocket}
 * guarantees the squatter is released regardless of test
 * outcome.
 */
class Scenario13Test {

    @Test
    void fixedPortConflictSurfacesBindException() throws Exception {
        try (ServerSocket squatter = new ServerSocket(Scenario13Constants.SQUATTED_PORT)) {
            assertThat(squatter.isBound())
                    .as("pre-bound the squatter ServerSocket")
                    .isTrue();

            var execution = EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(Scenario13Subject.class))
                    .execute();

            long failures = execution.allEvents().failed().count();
            assertThat(failures)
                    .as("deployment failed due to fixed-port conflict")
                    .isGreaterThan(0);

            boolean bindExceptionFound = execution.allEvents()
                    .failed()
                    .stream()
                    .map(e -> e.getPayload(org.junit.platform.engine.TestExecutionResult.class))
                    .flatMap(java.util.Optional::stream)
                    .map(org.junit.platform.engine.TestExecutionResult::getThrowable)
                    .flatMap(java.util.Optional::stream)
                    .anyMatch(Scenario13Test::throwableChainContainsBindException);

            assertThat(bindExceptionFound)
                    .as("the failure cause-chain contains a java.net.BindException")
                    .isTrue();
        }
    }

    private static boolean throwableChainContainsBindException(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof BindException) {
                return true;
            }
        }
        return false;
    }
}
