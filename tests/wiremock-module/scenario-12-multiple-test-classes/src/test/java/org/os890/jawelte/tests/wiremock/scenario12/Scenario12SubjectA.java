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
 * First test class for scenario 12. Records the OS-assigned port
 * it sees so {@link Scenario12Test} can compare it against
 * {@link Scenario12SubjectB}'s port.
 */
@EnableWireMock
class Scenario12SubjectA {

    @Inject
    private WireMockServer server;

    @Test
    void recordPort() {
        Scenario12PortRecorder.PORT_A.set(server.port());
    }
}
