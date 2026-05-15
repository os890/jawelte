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
package org.os890.jawelte.tests.wiremock.scenario02;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * Scenario 02 — stub registration. Registers a GET stub via the
 * injected {@link WireMock} client, issues an HTTP request against
 * the live {@link WireMockServer}'s base URL, and verifies that
 * the response body matches the stubbed value.
 *
 * <p>This is the first end-to-end HTTP scenario — proves the
 * lifecycle adapter actually boots a network-reachable server and
 * that the producer-supplied {@code WireMock} client is wired to
 * the same server instance the {@code WireMockServer} reference
 * points at.
 */
@EnableWireMock
class Scenario02Test {

    @Inject
    private WireMockServer server;

    @Inject
    private WireMock stubs;

    @Test
    void getReturnsStubbedBody() throws Exception {
        stubs.register(get(urlEqualTo("/hello"))
                .willReturn(aResponse().withStatus(200).withBody("hi")));

        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(server.baseUrl() + "/hello"))
                    .GET()
                    .build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        assertThat(response.statusCode())
                .as("WireMock served the stubbed GET /hello")
                .isEqualTo(200);
        assertThat(response.body())
                .as("response body matches the stub")
                .isEqualTo("hi");
    }
}
