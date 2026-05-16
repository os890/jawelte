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
package org.os890.jawelte.tests.wiremock.scenario21;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;

/**
 * Default-only mode injection of {@link WireMockRuntimeInfo}
 * (and re-checks for {@link WireMock} caching). The producer
 * builds the {@code WireMock} client and the
 * {@code WireMockRuntimeInfo} <b>once</b> at server registration
 * — both injection points must see the same Java instance the
 * registry cached.
 */
@EnableWireMock
class Scenario21Test {

    @Inject
    private WireMockServer server;

    @Inject
    private WireMockRuntimeInfo runtimeInfoA;

    @Inject
    private WireMockRuntimeInfo runtimeInfoB;

    @Inject
    private WireMock clientA;

    @Inject
    private WireMock clientB;

    @Test
    void runtimeInfoMetadataMatchesServer() {
        assertThat(runtimeInfoA.getHttpPort())
                .as("WireMockRuntimeInfo.getHttpPort matches the running server's port")
                .isEqualTo(server.port());
        assertThat(runtimeInfoA.getHttpBaseUrl())
                .as("WireMockRuntimeInfo.getHttpBaseUrl matches the running server's baseUrl")
                .isEqualTo(server.baseUrl());
    }

    @Test
    void runtimeInfoInstanceIsCachedAcrossInjections() {
        assertThat(runtimeInfoA)
                .as("the second WireMockRuntimeInfo injection resolves to the same cached instance")
                .isSameAs(runtimeInfoB);
    }

    @Test
    void wireMockClientInstanceIsCachedAcrossInjections() {
        assertThat(clientA)
                .as("the second WireMock client injection resolves to the same cached instance")
                .isSameAs(clientB);
    }
}
