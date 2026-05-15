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
package org.os890.jawelte.tests.jaxrs.scenario12;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.jaxrs.api.EnableJaxRs;
import org.os890.jawelte.module.jaxrs.api.TestUrl;

/**
 * Scenario 12 — {@code @EnableJaxRs} alone (no separate
 * {@code @EnableTestBeans} on the test class) boots the full
 * jawelte lifecycle: CDI container up, embedded JAX-RS server
 * running, {@link TestUrl} populated.
 *
 * <p>This works because {@code @EnableJaxRs} is meta-annotated
 * with {@code @EnableTestBeans} in jaxrs-module/api — JUnit
 * Jupiter walks the meta-annotation chain to discover the
 * {@code @ExtendWith(EnableTestBeans.Proxy.class)} that
 * {@code @EnableTestBeans} carries, so jawelte's proxy extension
 * registers automatically. The hex-arch is preserved:
 * jaxrs-module/api does NOT compile-depend on
 * {@code junit-jupiter-api} — the meta-annotation reference is
 * a plain Java annotation reference, and all JUnit interaction
 * stays in {@code core/api} (the bridge) and {@code core/impl}.
 *
 * <p>Test assertion: {@code @Inject TestUrl} resolves to a
 * populated URL holder, and an HTTP GET to {@code /hello}
 * succeeds — proving the full lifecycle ran end-to-end without
 * the user writing {@code @EnableTestBeans} on the class.
 */
@EnableJaxRs(restResources = {Scenario12HelloResource.class})
class Scenario12Test {

    @Inject
    private TestUrl testUrl;

    @Test
    void enableJaxRsAloneBootsBothCdiAndServer() {
        try (Client client = ClientBuilder.newClient()) {
            try (Response response = client
                    .target(testUrl.get() + "/hello")
                    .request()
                    .get()) {
                assertThat(response.getStatus())
                        .as("GET /hello returns 200 — server is up despite "
                                + "no explicit @EnableTestBeans on the test class")
                        .isEqualTo(200);
                assertThat(response.readEntity(String.class))
                        .as("response body matches the resource's literal return value")
                        .isEqualTo("hello");
            }
        }
    }
}
