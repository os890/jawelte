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
package org.os890.jawelte.tests.wiremock.scenario07;

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
 * Scenario 07 — {@code @Inject @PaymentApi WireMockServer} and
 * {@code @Inject @PaymentApi WireMock} resolve to the same
 * registered endpoint. Verified end-to-end: a stub registered via
 * the injected {@link WireMock} client fires when an HTTP request
 * is issued against the injected {@link WireMockServer}'s base URL.
 * Both injections must therefore be wired to the same underlying
 * server instance.
 */
@EnableWireMock
class Scenario07Test {

    @Inject
    @PaymentApi
    private WireMockServer server;

    @Inject
    @PaymentApi
    private WireMock client;

    @Test
    void clientAndServerShareTheSameEndpoint() throws Exception {
        client.register(get(urlEqualTo("/shared"))
                .willReturn(aResponse().withStatus(200).withBody("same-endpoint")));

        HttpResponse<String> response;
        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(server.baseUrl() + "/shared"))
                    .GET()
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }

        assertThat(response.statusCode())
                .as("@PaymentApi WireMock stub fires on @PaymentApi WireMockServer — both point at the same server")
                .isEqualTo(200);
        assertThat(response.body())
                .as("response body matches the stub registered on the @PaymentApi WireMock client")
                .isEqualTo("same-endpoint");
    }
}
