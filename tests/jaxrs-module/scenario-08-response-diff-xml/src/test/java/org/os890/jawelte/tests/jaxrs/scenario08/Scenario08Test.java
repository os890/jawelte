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
package org.os890.jawelte.tests.jaxrs.scenario08;

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
 * Scenario 08 — the XML analogue of scenario 07:
 * {@link ResponseDiff#forXml} reads the response entity as a
 * {@code String}, hands it to {@code ContentDiff.forXml}, and
 * {@code assertEquals} succeeds when the body matches the
 * expected content.
 */
@EnableTestBeans
@EnableJaxRs(restResources = {Scenario08OrderResource.class})
class Scenario08Test {

    @Inject
    private TestUrl testUrl;

    @Test
    void responseDiffForXmlAssertsEquality() {
        try (Client client = ClientBuilder.newClient()) {
            try (Response response = client
                    .target(testUrl.get() + "/order")
                    .request()
                    .get()) {
                ResponseDiff.forXml(response)
                        .expectedContent("<order><id>1</id><name>Widget</name></order>")
                        .assertEquals();
            }
        }
    }
}
