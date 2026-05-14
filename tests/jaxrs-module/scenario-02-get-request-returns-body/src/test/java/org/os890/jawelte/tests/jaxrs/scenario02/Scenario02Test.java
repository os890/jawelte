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
package org.os890.jawelte.tests.jaxrs.scenario02;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jaxrs.api.EnableJaxRs;
import org.os890.jawelte.module.jaxrs.api.TestUrl;

/**
 * Scenario 02 — an HTTP {@code GET} against the embedded server's
 * {@code /hello} endpoint returns {@code 200 OK} with the
 * resource's literal body. Exercises the basic JAX-RS dispatch
 * path: client → embedded server → CdiIntegrationFilter →
 * resource method → response.
 */
@EnableTestBeans
@EnableJaxRs(restResources = {Scenario02HelloResource.class})
class Scenario02Test {

    @Inject
    private TestUrl testUrl;

    @Test
    void getReturnsTwoHundredWithExpectedBody() {
        try (Client client = ClientBuilder.newClient()) {
            try (Response response = client
                    .target(testUrl.get() + "/hello")
                    .request()
                    .get()) {
                assertThat(response.getStatus())
                        .as("GET /hello returns 200 OK")
                        .isEqualTo(200);
                assertThat(response.readEntity(String.class))
                        .as("response body matches the resource's literal return value")
                        .isEqualTo("hello");
            }
        }
    }
}
