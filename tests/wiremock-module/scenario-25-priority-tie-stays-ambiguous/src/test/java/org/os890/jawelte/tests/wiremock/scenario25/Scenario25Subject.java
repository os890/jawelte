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
package org.os890.jawelte.tests.wiremock.scenario25;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Subject for scenario 25 — two qualifiers tied at
 * {@code @Priority(1)} plus an unqualified
 * {@code @Inject WireMockServer}. With no clear priority
 * winner, the CDI extension falls back to the legacy "every
 * synthetic bean carries {@code @Default}" rule and the
 * unqualified injection fails at deployment with the standard
 * {@code AmbiguousResolutionException}.
 */
@EnableWireMock
class Scenario25Subject {

    @Inject
    @AlphaApi
    private WireMockServer alphaServer;

    @Inject
    @BetaApi
    private WireMockServer betaServer;

    @Inject
    private WireMockServer unqualified;

    @Test
    void unreachable() {
        // Deployment must fail before this method runs.
    }
}
