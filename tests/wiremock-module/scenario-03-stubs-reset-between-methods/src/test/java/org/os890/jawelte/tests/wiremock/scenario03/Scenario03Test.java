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
package org.os890.jawelte.tests.wiremock.scenario03;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * Scenario 03 — stubs reset between test methods. Method 1
 * registers a stub for {@code GET /persisted} and verifies the
 * server returns it. Method 2 hits the same path and expects HTTP
 * 404 — the lifecycle adapter's {@code beforeEach} called
 * {@code WireMockServer.resetAll()} between methods.
 *
 * <p>Uses {@code @TestMethodOrder(OrderAnnotation.class)} +
 * {@code @Order} to make the inter-method dependency deterministic
 * (the second method's expectation depends on the first having
 * registered the stub).
 */
@TestMethodOrder(OrderAnnotation.class)
@EnableWireMock
class Scenario03Test {

    @Inject
    private WireMockServer server;

    @Inject
    private WireMock stubs;

    @Test
    @Order(1)
    void firstMethodRegistersStub() throws Exception {
        stubs.register(get(urlEqualTo("/persisted"))
                .willReturn(aResponse().withStatus(200).withBody("from-1st-method")));

        HttpResponse<String> response = send("/persisted");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("from-1st-method");
    }

    @Test
    @Order(2)
    void secondMethodSeesNoStubs() throws Exception {
        HttpResponse<String> response = send("/persisted");
        assertThat(response.statusCode())
                .as("the lifecycle adapter ran resetAll() between methods — the previous stub is gone")
                .isEqualTo(404);
    }

    private HttpResponse<String> send(String path) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(server.baseUrl() + path))
                    .GET()
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
