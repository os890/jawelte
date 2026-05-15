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
package org.os890.jawelte.tests.wiremock.scenario12;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Second test class for scenario 12 — independent of
 * {@link Scenario12SubjectA}. {@link Scenario12Test} runs both
 * via {@code EngineTestKit} and asserts the recorded ports
 * differ.
 */
@EnableWireMock
class Scenario12SubjectB {

    @Inject
    private WireMockServer server;

    @Test
    void recordPort() {
        Scenario12PortRecorder.PORT_B.set(server.port());
    }
}
