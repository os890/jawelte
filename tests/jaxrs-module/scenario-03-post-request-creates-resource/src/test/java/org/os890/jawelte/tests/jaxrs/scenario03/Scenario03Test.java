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
package org.os890.jawelte.tests.jaxrs.scenario03;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jaxrs.api.EnableJaxRs;
import org.os890.jawelte.module.jaxrs.api.TestUrl;

/**
 * Scenario 03 — an HTTP {@code POST} with a JSON-typed body
 * against the embedded server returns {@code 201 Created} and the
 * server-side resource bean observes the body the client sent.
 *
 * <p>Exercises both HTTP-level POST handling and CDI integration
 * in the resource: {@link Scenario03OrderResource} injects
 * {@link ReceivedOrderHolder} and stores the raw JSON body on it;
 * the test thread reads the holder back through its own
 * {@code @Inject}-acquired reference, asserting both the status
 * and the cross-thread observation.
 */
@EnableTestBeans
@EnableJaxRs(restResources = {Scenario03OrderResource.class})
class Scenario03Test {

    private static final String ORDER_JSON = "{\"name\":\"Widget\"}";

    @Inject
    private TestUrl testUrl;

    @Inject
    private ReceivedOrderHolder holder;

    @Test
    void postReturnsTwoOhOneAndResourceSeesBody() {
        try (Client client = ClientBuilder.newClient()) {
            try (Response response = client
                    .target(testUrl.get() + "/orders")
                    .request()
                    .post(Entity.entity(ORDER_JSON, MediaType.APPLICATION_JSON))) {
                assertThat(response.getStatus())
                        .as("POST /orders returns 201 Created")
                        .isEqualTo(201);
            }
        }
        assertThat(holder.getBody())
                .as("server-side resource bean observed the JSON body the client posted")
                .isEqualTo(ORDER_JSON);
    }
}
