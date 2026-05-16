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
package org.os890.jawelte.tests.wiremock.scenario14;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Scenario 14 — HTTPS state when not configured. The module does
 * not configure HTTPS on its own. WireMock 3.x's
 * {@code server.httpsPort()} throws {@code IllegalStateException}
 * when no HTTPS listener is active, and
 * {@code server.getOptions().httpsSettings().enabled()} returns
 * {@code false}. The library's HTTPS settings are a WireMock
 * concern; this scenario locks in the documented default state
 * the module ships with.
 */
@EnableWireMock
class Scenario14Test {

    @Inject
    private WireMockServer server;

    @Test
    void httpsIsDisabledByDefault() {
        assertThat(server.getOptions().httpsSettings().enabled())
                .as("default WireMockServer reports HTTPS disabled")
                .isFalse();
        assertThatThrownBy(server::httpsPort)
                .as("WireMock 3.x raises IllegalStateException when HTTPS is not configured")
                .isInstanceOf(IllegalStateException.class);
    }
}
