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
package org.os890.jawelte.tests.wiremock.scenario04;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Scenario 04 — two {@code @WireMockEndpoint}-stamped qualifiers
 * with fixed ports (18081, 18082). Verify that each server bound
 * to its declared port and the two servers are distinct
 * {@link WireMockServer} instances.
 */
@EnableWireMock
class Scenario04Test {

    @Inject
    @PaymentApi
    private WireMockServer paymentServer;

    @Inject
    @InventoryApi
    private WireMockServer inventoryServer;

    @Test
    void serversBindToTheirDeclaredFixedPorts() {
        assertThat(paymentServer.port())
                .as("@PaymentApi server bound to its declared port")
                .isEqualTo(18081);
        assertThat(inventoryServer.port())
                .as("@InventoryApi server bound to its declared port")
                .isEqualTo(18082);
        assertThat(paymentServer)
                .as("the two qualified injections resolve to distinct WireMockServer instances")
                .isNotSameAs(inventoryServer);
    }
}
