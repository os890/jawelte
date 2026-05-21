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
package example.responsediff;

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
 * ResponseDiff.forJson(response) reads the JAX-RS Response entity as
 * a String, hands it to ContentDiff.forJson, and lets the test assert
 * semantic JSON equality directly on the response — no manual
 * response.readEntity(String.class).equals(...).
 */
@EnableTestBeans
@EnableJaxRs(restResources = {OrderResource.class})
class OrderResourceTest {

    @Inject
    TestUrl testUrl;

    @Test
    void responseBodyMatchesExpectedJson() {
        try (Client client = ClientBuilder.newClient()) {
            try (Response response = client.target(testUrl.get() + "/order").request().get()) {
                ResponseDiff.forJson(response)
                        .expectedContent("{\"name\":\"Widget\",\"id\":1}")   // different key order
                        .assertEquals();
            }
        }
    }
}
