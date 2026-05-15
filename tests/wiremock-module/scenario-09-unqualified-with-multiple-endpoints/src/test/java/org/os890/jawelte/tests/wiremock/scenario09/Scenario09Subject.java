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
package org.os890.jawelte.tests.wiremock.scenario09;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Subject class for scenario 09. Has two
 * {@code @WireMockEndpoint}-stamped qualifiers ({@code @PaymentApi}
 * and {@code @InventoryApi}) <b>and</b> an unqualified
 * {@code @Inject WireMockServer} — both synthetic beans carry
 * {@code @Default}, so deployment must fail with
 * {@code AmbiguousResolutionException}.
 *
 * <p>The Surefire pattern {@code *Test} skips this class; it is
 * launched by {@link Scenario09Test} via {@code EngineTestKit}.
 */
@EnableWireMock
class Scenario09Subject {

    @Inject
    @PaymentApi
    private WireMockServer paymentServer;

    @Inject
    @InventoryApi
    private WireMockServer inventoryServer;

    @Inject
    private WireMockServer unqualified;

    @Test
    void unreachable() {
        // Body intentionally empty — deployment must fail before
        // this method runs. If it ever does, it would be a
        // regression of the multi-endpoint ambiguity contract.
    }
}
