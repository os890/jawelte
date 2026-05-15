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
package org.os890.jawelte.tests.jaxrs.scenario18;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.jaxrs.api.EnableJaxRs;
import org.os890.jawelte.module.jaxrs.api.TestUrl;

/**
 * Scenario 18 — a plain Java {@code @Path}-annotated class
 * (not a CDI bean) registered via
 * {@code @EnableJaxRs(restResources = ...)} still serves HTTP
 * traffic. The lifecycle adapter detects CDI's
 * {@code UnsatisfiedResolutionException} on the lookup and falls
 * back to the resource's public no-arg constructor, registering
 * the resulting instance with the JAX-RS application as a
 * singleton.
 *
 * <p>This lets users add stateless, non-CDI resource classes
 * without having to mark them with a CDI scope. Such resources
 * cannot use {@code @Inject} (since they aren't CDI-managed),
 * but the typical case — a tiny stateless endpoint — is
 * supported with no extra annotations.
 */
@EnableJaxRs(restResources = {Scenario18PlainResource.class})
class Scenario18Test {

    @Inject
    private TestUrl testUrl;

    @Test
    void plainNonCdiResourceIsServedViaReflectiveFallback() {
        try (Client client = ClientBuilder.newClient()) {
            try (Response response = client
                    .target(testUrl.get() + "/plain")
                    .request()
                    .get()) {
                assertThat(response.getStatus())
                        .as("GET /plain returns 200 — the resource was "
                                + "instantiated by the reflective fallback")
                        .isEqualTo(200);
                assertThat(response.readEntity(String.class))
                        .as("response body matches the resource's literal return value")
                        .isEqualTo("plain-hello");
            }
        }
    }
}
