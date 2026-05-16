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
package org.os890.jawelte.tests.wiremock.scenario10;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Subject for scenario 10. Captures the injected
 * {@link WireMockServer} reference into
 * {@link Scenario10ServerHolder#SERVER} during the test method.
 * After {@link Scenario10Test}'s {@code EngineTestKit} call
 * returns, the lifecycle adapter's {@code afterAll} has run and
 * stopped the server; the held reference's
 * {@link WireMockServer#isRunning()} must report {@code false}.
 */
@EnableWireMock
class Scenario10Subject {

    @Inject
    private WireMockServer server;

    @Test
    void captureServerReference() {
        Scenario10ServerHolder.SERVER.set(server);
        if (!server.isRunning()) {
            throw new IllegalStateException(
                    "WireMockServer not running during the test method");
        }
    }
}
