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
package org.os890.jawelte.tests.jaxrs.scenario05;

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
 * Scenario 05 — two HTTP requests in the same test method
 * dispatch through the resource and read its {@code @RequestScoped}
 * injection's identity hash. Two distinct
 * {@link Scenario05PerRequestBean} instances are expected — proves
 * the {@code CdiIntegrationFilter} request-context cycle
 * (activate→dispatch→deactivate) runs per HTTP request.
 */
@EnableTestBeans
@EnableJaxRs(restResources = {Scenario05IdentityResource.class})
class Scenario05Test {

    @Inject
    private TestUrl testUrl;

    @Test
    void requestScopedBeanIsRecreatedPerHttpRequest() {
        String firstIdentity = readIdentity();
        String secondIdentity = readIdentity();

        assertThat(firstIdentity)
                .as("identity reported in request 1 is non-empty")
                .isNotBlank();
        assertThat(secondIdentity)
                .as("identity reported in request 2 is non-empty")
                .isNotBlank();
        assertThat(secondIdentity)
                .as("request 2's @RequestScoped instance is distinct from request 1's")
                .isNotEqualTo(firstIdentity);
    }

    private String readIdentity() {
        try (Client client = ClientBuilder.newClient()) {
            try (Response response = client
                    .target(testUrl.get() + "/identity")
                    .request()
                    .get()) {
                assertThat(response.getStatus()).isEqualTo(200);
                return response.readEntity(String.class);
            }
        }
    }
}
