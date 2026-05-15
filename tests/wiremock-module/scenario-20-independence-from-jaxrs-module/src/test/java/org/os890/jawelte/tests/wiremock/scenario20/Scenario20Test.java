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
package org.os890.jawelte.tests.wiremock.scenario20;

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
import org.os890.jawelte.module.jaxrs.api.EnableJaxRs;
import org.os890.jawelte.module.jaxrs.api.TestUrl;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * Scenario 20 — wiremock-module and jaxrs-module on the same
 * test class. Both adapters live at {@code @Priority(75)}; their
 * relative ordering is undefined. The test verifies both servers
 * are independently reachable and serving their respective
 * routes — no port collision, no cross-talk.
 *
 * <p>The two annotation declarations are kept side by side so
 * the dual-module setup is obvious from a single
 * line.
 */
@EnableJaxRs(restResources = {Scenario20JaxRsResource.class})
@EnableWireMock
class Scenario20Test {

    @Inject
    private TestUrl jaxrsBaseUrl;

    @Inject
    private WireMockServer wireMockServer;

    @Inject
    private WireMock wireMockClient;

    @Test
    void bothServersAreReachableOnDistinctPorts() throws Exception {
        wireMockClient.register(get(urlEqualTo("/scenario-20/wiremock-ping"))
                .willReturn(aResponse().withStatus(200).withBody("wiremock-alive")));

        HttpResponse<String> jaxrsResponse;
        HttpResponse<String> wiremockResponse;
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest jaxrsRequest = HttpRequest.newBuilder()
                    .uri(URI.create(jaxrsBaseUrl.get() + "/scenario-20/jaxrs-ping"))
                    .GET()
                    .build();
            jaxrsResponse = client.send(jaxrsRequest, HttpResponse.BodyHandlers.ofString());

            HttpRequest wmRequest = HttpRequest.newBuilder()
                    .uri(URI.create(wireMockServer.baseUrl() + "/scenario-20/wiremock-ping"))
                    .GET()
                    .build();
            wiremockResponse = client.send(wmRequest, HttpResponse.BodyHandlers.ofString());
        }

        assertThat(jaxrsResponse.statusCode()).as("JAX-RS server served the GET").isEqualTo(200);
        assertThat(jaxrsResponse.body()).as("JAX-RS resource body").isEqualTo("jaxrs-alive");
        assertThat(wiremockResponse.statusCode()).as("WireMock server served the GET").isEqualTo(200);
        assertThat(wiremockResponse.body()).as("WireMock stub body").isEqualTo("wiremock-alive");

        int jaxrsPort = URI.create(jaxrsBaseUrl.get()).getPort();
        int wireMockPort = wireMockServer.port();
        assertThat(jaxrsPort)
                .as("JAX-RS and WireMock landed on different ports")
                .isNotEqualTo(wireMockPort);
    }
}
