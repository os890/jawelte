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
package org.os890.jawelte.tests.wiremock.scenario22;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;

/**
 * Qualified-endpoint injection of {@link WireMockRuntimeInfo}.
 * The synthetic bean registered for {@code @PaymentApi} returns
 * the cached {@code WireMockRuntimeInfo} from the
 * {@code EndpointResources} bundle — repeated injections must
 * yield the same Java instance, and the metadata must match the
 * paired {@link WireMockServer}.
 */
@EnableWireMock
class Scenario22Test {

    @Inject
    @PaymentApi
    private WireMockServer server;

    @Inject
    @PaymentApi
    private WireMockRuntimeInfo runtimeInfoA;

    @Inject
    @PaymentApi
    private WireMockRuntimeInfo runtimeInfoB;

    @Inject
    @PaymentApi
    private WireMock clientA;

    @Inject
    @PaymentApi
    private WireMock clientB;

    @Test
    void qualifiedRuntimeInfoMatchesQualifiedServer() {
        assertThat(runtimeInfoA.getHttpPort())
                .as("@PaymentApi WireMockRuntimeInfo.getHttpPort matches the @PaymentApi server")
                .isEqualTo(server.port());
        assertThat(runtimeInfoA.getHttpBaseUrl())
                .as("@PaymentApi WireMockRuntimeInfo.getHttpBaseUrl matches the @PaymentApi server")
                .isEqualTo(server.baseUrl());
    }

    @Test
    void qualifiedRuntimeInfoIsCachedAcrossInjections() {
        assertThat(runtimeInfoA)
                .as("two qualified WireMockRuntimeInfo injections resolve to the same cached instance")
                .isSameAs(runtimeInfoB);
    }

    @Test
    void qualifiedWireMockClientIsCachedAcrossInjections() {
        assertThat(clientA)
                .as("two qualified WireMock client injections resolve to the same cached instance")
                .isSameAs(clientB);
    }
}
