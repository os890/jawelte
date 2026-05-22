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
package example.wiremock.priority;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Two qualifiers, one of which ({@link PaymentApi}) carries
 * {@code @Priority(1)}. wiremock-module's CDI extension picks the
 * strict-minimum priority value and binds the corresponding synthetic
 * bean as the implicit {@code @Default}. An unqualified
 * {@code @Inject WireMockServer} resolves to that server; qualified
 * injections still follow standard CDI rules.
 */
@EnableWireMock
class PriorityDefaultTest {

    @Inject @PaymentApi
    WireMockServer paymentServer;

    @Inject @InventoryApi
    WireMockServer inventoryServer;

    @Inject
    WireMockServer implicitDefault;

    @Test
    void unqualifiedInjectionResolvesToThePriorityWinner() {
        assertThat(implicitDefault)
                .as("unqualified @Inject resolves to @PaymentApi (the @Priority(1) qualifier)")
                .isSameAs(paymentServer);
    }

    @Test
    void qualifiedInjectionsStillReachTheirOwnServers() {
        assertThat(paymentServer.port())
                .as("each endpoint is bound to its own OS-assigned port")
                .isNotEqualTo(inventoryServer.port());
        assertThat(paymentServer)
                .as("@PaymentApi server and @InventoryApi server are distinct instances")
                .isNotSameAs(inventoryServer);
    }
}
