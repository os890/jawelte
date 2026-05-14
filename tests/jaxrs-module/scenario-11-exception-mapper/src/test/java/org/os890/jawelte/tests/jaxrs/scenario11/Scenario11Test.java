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
package org.os890.jawelte.tests.jaxrs.scenario11;

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
 * Scenario 11 — a {@code @Provider}-annotated, CDI-managed
 * {@code ExceptionMapper} registered via
 * {@code restResources} converts a resource-thrown exception to
 * a 418 response with a custom body.
 */
@EnableTestBeans
@EnableJaxRs(restResources = {Scenario11Resource.class, Scenario11TeapotExceptionMapper.class})
class Scenario11Test {

    @Inject
    private TestUrl testUrl;

    @Test
    void mapperConvertsExceptionToCustomStatusAndBody() {
        try (Client client = ClientBuilder.newClient()) {
            try (Response response = client
                    .target(testUrl.get() + "/brew")
                    .request()
                    .get()) {
                assertThat(response.getStatus())
                        .as("ExceptionMapper produced HTTP 418")
                        .isEqualTo(418);
                assertThat(response.readEntity(String.class))
                        .as("ExceptionMapper supplied the body")
                        .isEqualTo("teapot");
            }
        }
    }
}
