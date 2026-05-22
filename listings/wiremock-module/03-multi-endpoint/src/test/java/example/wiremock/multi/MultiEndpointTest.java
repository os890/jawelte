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
package example.wiremock.multi;

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

/**
 * Two upstreams in one test class: {@code @PaymentApi} and
 * {@code @InventoryApi} qualifiers each boot their own
 * {@code WireMockServer} on an OS-assigned port, and the test stubs +
 * calls each independently. Stub registration on one server is
 * invisible to the other — per-endpoint isolation is the whole point
 * of the multi-endpoint mode.
 *
 * <p>The same upstream {@code WireMock} stub-registration client used
 * in listing 02 is reachable via {@code server.stubFor(...)} on each
 * injected {@code WireMockServer}; the dedicated {@code @Inject WireMock}
 * field shape is omitted here so the listing stays focused on the
 * per-qualifier server-injection mechanic.
 */
@EnableWireMock
class MultiEndpointTest {

    @Inject @PaymentApi
    WireMockServer paymentServer;

    @Inject @InventoryApi
    WireMockServer inventoryServer;

    @Test
    void eachQualifierGetsItsOwnServerAndStubsAreIsolated() throws Exception {
        // Each server bound to a different OS-assigned port.
        assertThat(paymentServer.port()).isNotEqualTo(inventoryServer.port());

        // Register a stub on each upstream — same path, different bodies.
        paymentServer.stubFor(get(urlEqualTo("/hello"))
                .willReturn(aResponse().withStatus(200).withBody("from-payment")));
        inventoryServer.stubFor(get(urlEqualTo("/hello"))
                .willReturn(aResponse().withStatus(200).withBody("from-inventory")));

        // Call each upstream and verify the bodies cross-check.
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> paymentResp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(paymentServer.baseUrl() + "/hello")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> inventoryResp = client.send(
                    HttpRequest.newBuilder().uri(URI.create(inventoryServer.baseUrl() + "/hello")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(paymentResp.body()).isEqualTo("from-payment");
            assertThat(inventoryResp.body()).isEqualTo("from-inventory");
        }
    }
}
