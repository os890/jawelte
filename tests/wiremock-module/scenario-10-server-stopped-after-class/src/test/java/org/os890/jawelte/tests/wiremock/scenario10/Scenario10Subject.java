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
 * Subject for scenario 10. {@link Scenario10Test} runs it via
 * {@code EngineTestKit}; after the engine returns,
 * {@link Scenario10StopRecorder#FIRED_COUNT} should be exactly
 * {@code 1} — the lifecycle adapter fired
 * {@code WireMockServersStopped} once in its {@code afterAll}.
 */
@EnableWireMock
class Scenario10Subject {

    @Inject
    private WireMockServer server;

    @Test
    void serverIsRunningDuringTest() {
        // No-op probe — the subject needs at least one running
        // test method so JUnit reaches the @AfterAll path that
        // drives wiremock-module's afterAll.
        if (server.port() <= 0) {
            throw new IllegalStateException("WireMockServer not running");
        }
    }
}
