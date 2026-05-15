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
package org.os890.jawelte.tests.jaxrs.scenario07;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jaxrs.api.EnableJaxRs;
import org.os890.jawelte.module.jaxrs.api.ResponseDiff;
import org.os890.jawelte.module.jaxrs.api.TestUrl;

/**
 * Scenario 07 — verifies that {@link ResponseDiff#forJson} reads
 * the JAX-RS {@link Response} entity as a {@code String}, hands
 * it to {@code ContentDiff.forJson}, and the resulting builder's
 * {@code assertEquals} succeeds when the response body matches
 * the supplied expected content.
 *
 * <p>This is the happy-path bridge test for ResponseDiff JSON;
 * mismatch and ignore-pattern behaviour lives in TICKET-008's
 * own scenarios.
 */
@EnableTestBeans
@EnableJaxRs(restResources = {Scenario07OrderResource.class})
class Scenario07Test {

    @Inject
    private TestUrl testUrl;

    @Test
    void responseDiffForJsonAssertsEquality() {
        try (Client client = ClientBuilder.newClient()) {
            try (Response response = client
                    .target(testUrl.get() + "/order")
                    .request()
                    .get()) {
                ResponseDiff.forJson(response)
                        .expectedContent("{\"id\":1,\"name\":\"Widget\"}")
                        .assertEquals();
            }
        }
    }
}
