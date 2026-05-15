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
package org.os890.jawelte.tests.jaxrs.scenario04;

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
 * Scenario 04 — a JAX-RS resource injects an
 * {@code @ApplicationScoped} service via {@code @Inject} and the
 * service method is invoked when an HTTP request hits the resource.
 *
 * <p>Verifies that the singleton-via-CDI resource resolution path
 * in {@code JaxRsLifecycleAdapter} satisfies the resource's
 * injection points — without it, the {@code @Inject} field would
 * be {@code null} and the resource would NPE before returning.
 */
@EnableTestBeans
@EnableJaxRs(restResources = {Scenario04GreetResource.class})
class Scenario04Test {

    @Inject
    private TestUrl testUrl;

    @Test
    void resourceUsesInjectedService() {
        try (Client client = ClientBuilder.newClient()) {
            try (Response response = client
                    .target(testUrl.get() + "/greet/world")
                    .request()
                    .get()) {
                assertThat(response.getStatus())
                        .as("GET /greet/world returns 200")
                        .isEqualTo(200);
                assertThat(response.readEntity(String.class))
                        .as("response body is composed by the @Inject-ed service")
                        .isEqualTo("Hello, world");
            }
        }
    }
}
