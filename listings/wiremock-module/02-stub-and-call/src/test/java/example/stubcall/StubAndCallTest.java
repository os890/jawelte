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
package example.stubcall;

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

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;

@EnableWireMock
class StubAndCallTest {

    @Inject
    WireMockRuntimeInfo runtimeInfo;

    @Inject
    WireMock stubs;

    @Test
    void getReturnsStubbedBody() throws Exception {
        stubs.register(get(urlEqualTo("/hello"))
                .willReturn(aResponse().withStatus(200).withBody("hi")));

        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(runtimeInfo.getHttpBaseUrl() + "/hello"))
                    .GET()
                    .build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("hi");
    }
}
