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
package org.os890.jawelte.tests.wiremock.scenario24;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Scenario 24 — multi-endpoint mode with {@code @Priority} on
 * one qualifier. {@link PaymentApi} carries
 * {@code @Priority(1)}; {@link InventoryApi} carries no priority.
 * The CDI extension declares {@code @PaymentApi} as the implicit
 * {@code @Default} winner: its synthetic bean keeps
 * {@code @Default + @PaymentApi}, the {@code @InventoryApi}
 * synthetic bean carries only {@code @InventoryApi}.
 *
 * <p>Verified end-to-end: an unqualified {@code @Inject WireMockServer}
 * resolves to the {@code @PaymentApi} server (port 19101), while
 * the explicitly-qualified injections continue to follow standard
 * CDI rules.
 */
@EnableWireMock
class Scenario24Test {

    @Inject
    @PaymentApi
    private WireMockServer paymentServer;

    @Inject
    @InventoryApi
    private WireMockServer inventoryServer;

    @Inject
    private WireMockServer implicitDefault;

    @Test
    void unqualifiedInjectionResolvesToThePriorityWinner() {
        assertThat(implicitDefault.port())
                .as("unqualified @Inject resolved to the @Priority(1) qualifier @PaymentApi (port 19101)")
                .isEqualTo(19101);
        assertThat(implicitDefault)
                .as("unqualified injection points at the same server as @PaymentApi")
                .isSameAs(paymentServer);
    }

    @Test
    void qualifiedInjectionsStillFollowStandardCdiRules() {
        assertThat(paymentServer.port())
                .as("@PaymentApi server bound to its declared fixed port")
                .isEqualTo(19101);
        assertThat(inventoryServer.port())
                .as("@InventoryApi server bound to its port — qualified injection unaffected by @Priority")
                .isEqualTo(19102);
        assertThat(paymentServer)
                .as("the two endpoint servers are distinct WireMockServer instances")
                .isNotSameAs(inventoryServer);
    }
}
