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
package org.os890.jawelte.tests.wiremock.scenario05;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Scenario 05 — {@code @WireMockEndpoint} with the default
 * {@code port=0}. Verify the OS-assigned port is strictly positive
 * (a valid ephemeral port).
 */
@EnableWireMock
class Scenario05Test {

    @Inject
    @InventoryApi
    private WireMockServer inventoryServer;

    @Test
    void osAssignedPortIsPositive() {
        assertThat(inventoryServer.port())
                .as("OS-assigned port is strictly positive (never 0, never -1)")
                .isGreaterThan(0);
    }
}
