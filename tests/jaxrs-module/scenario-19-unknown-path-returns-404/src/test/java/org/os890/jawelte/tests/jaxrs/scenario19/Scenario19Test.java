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
package org.os890.jawelte.tests.jaxrs.scenario19;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.jaxrs.api.EnableJaxRs;
import org.os890.jawelte.module.jaxrs.api.TestUrl;

/**
 * Scenario 19 — verifies that the embedded server returns
 * {@code 404 Not Found} for a path that no registered resource
 * handles. Exercises the default JAX-RS routing fall-through:
 * the server is up, dispatch happens, no resource matches,
 * standard 404 response.
 */
@EnableJaxRs(restResources = {Scenario19HelloResource.class})
class Scenario19Test {

    @Inject
    private TestUrl testUrl;

    @Test
    void unmappedPathReturnsNotFound() {
        try (Client client = ClientBuilder.newClient()) {
            try (Response response = client
                    .target(testUrl.get() + "/nonexistent")
                    .request()
                    .get()) {
                assertThat(response.getStatus())
                        .as("GET /nonexistent returns 404 — no resource registered for this path")
                        .isEqualTo(404);
            }
        }
    }
}
