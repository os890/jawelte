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
package org.os890.jawelte.tests.jaxrs.scenario09;

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
 * Scenario 09 — two resource classes passed via
 * {@code @EnableJaxRs(restResources = {A.class, B.class})} are
 * both reachable on the same embedded server, each at its own
 * {@code @Path}.
 */
@EnableTestBeans
@EnableJaxRs(restResources = {Scenario09ResourceA.class, Scenario09ResourceB.class})
class Scenario09Test {

    @Inject
    private TestUrl testUrl;

    @Test
    void bothResourcesAreReachable() {
        assertThat(get("/a"))
                .as("/a is served by Scenario09ResourceA")
                .isEqualTo("A");
        assertThat(get("/b"))
                .as("/b is served by Scenario09ResourceB")
                .isEqualTo("B");
    }

    private String get(String path) {
        try (Client client = ClientBuilder.newClient()) {
            try (Response response = client
                    .target(testUrl.get() + path)
                    .request()
                    .get()) {
                assertThat(response.getStatus())
                        .as("GET %s returns 200", path)
                        .isEqualTo(200);
                return response.readEntity(String.class);
            }
        }
    }
}
