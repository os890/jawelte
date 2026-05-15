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
package org.os890.jawelte.tests.wiremock.scenario01;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Scenario 01 — default-only mode. {@code @EnableWireMock} on the
 * test class with no {@code @WireMockEndpoint}-stamped qualifier
 * anywhere in the hierarchy. The lifecycle adapter starts exactly
 * one {@code WireMockServer} on an OS-assigned port and registers
 * it under {@code Default.class}. {@code WireMockProducer}'s
 * {@code @Default @Produces} method satisfies the unqualified
 * {@code @Inject WireMockServer}.
 *
 * <p>Smoke test of the wiremock-module lifecycle: verifies the
 * adapter ran (server is non-null), the port was OS-assigned
 * (strictly positive, never the placeholder {@code 0} or
 * {@code -1}), and the base URL string matches the documented
 * {@code "http://localhost:{port}"} shape.
 */
@EnableWireMock
class Scenario01Test {

    @Inject
    private WireMockServer server;

    @Test
    void defaultServerIsRunningOnOsAssignedPort() {
        assertThat(server)
                .as("WireMockProducer's @Default WireMockServer is injected")
                .isNotNull();
        assertThat(server.port())
                .as("OS-assigned port is strictly positive (never 0, never -1)")
                .isGreaterThan(0);
        assertThat(server.baseUrl())
                .as("baseUrl follows the documented http://localhost:{port} shape")
                .startsWith("http://localhost:");
    }
}
