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
package org.os890.jawelte.tests.lnp.scenario06;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;
import org.os890.jawelte.module.jaxrs.api.TestUrl;
import org.os890.jawelte.module.testcontrol.api.TestControl;

/**
 * Drives a single full CRUD roundtrip per test method:
 *
 * <ol>
 *   <li>GET /customers — initial state (5 seed rows)</li>
 *   <li>POST /customers — insert a 6th row</li>
 *   <li>GET /customers — confirm the 6 rows</li>
 *   <li>PUT /customers/6/email — update the new row's email</li>
 *   <li>GET /customers/6 — confirm the update</li>
 *   <li>DELETE /customers/6 — drop the row</li>
 *   <li>GET /customers — back to the original 5 rows</li>
 * </ol>
 *
 * <p>Each step asserts the response body via
 * {@link ContentDiff#forJson} against an on-disk fixture under
 * {@code src/test/resources/lnp-roundtrip/expected-responses/}.
 * After the test method returns, db-testdata-module verifies the
 * customer table is back to the original 5 rows by comparing the
 * actual content to {@code lnp-roundtrip/seed/dbExpected/full.xml}.
 *
 * <p>The numbered subclasses repeat this whole sequence N=50 times
 * per JVM so the LNP sweep can compare per-class overhead against
 * scenarios 01-05.
 */
public abstract class AbstractFullCrudRoundtripScenarioTest {

    private static final String EXPECTED_BASE = "lnp-roundtrip/expected-responses/";

    @Inject
    private TestUrl testUrl;

    /** Default constructor required by JUnit/CDI. */
    protected AbstractFullCrudRoundtripScenarioTest() {
    }

    @Test
    @TestControl(testData = "lnp-roundtrip/seed")
    public void fullCrudRoundtrip() {
        try (Client client = ClientBuilder.newClient()) {
            // 1. initial GET — 5 seed rows
            invoke(client, "GET", "/customers", null, EXPECTED_BASE + "01-list-initial.json");
            // 2. POST — insert id=6
            invoke(client, "POST",
                    "/customers?name=Customer-6&email=customer6@test.com",
                    "", EXPECTED_BASE + "02-create.json");
            // 3. GET — 6 rows visible
            invoke(client, "GET", "/customers", null, EXPECTED_BASE + "03-list-after-create.json");
            // 4. PUT — update email
            invoke(client, "PUT", "/customers/6/email?value=updated@test.com",
                    "", EXPECTED_BASE + "04-update-email.json");
            // 5. GET id=6 — confirm update
            invoke(client, "GET", "/customers/6", null, EXPECTED_BASE + "05-read-updated.json");
            // 6. DELETE id=6
            invoke(client, "DELETE", "/customers/6", null, EXPECTED_BASE + "06-delete.json");
            // 7. final GET — back to 5 rows
            invoke(client, "GET", "/customers", null, EXPECTED_BASE + "07-list-final.json");
        }
    }

    private void invoke(Client client, String method, String pathAndQuery,
                        String body, String classpathResource) {
        String url = testUrl.get() + pathAndQuery;
        Invocation.Builder request = client.target(url)
                .request(MediaType.APPLICATION_JSON);
        Response response = (body == null)
                ? request.method(method)
                : request.method(method,
                        Entity.entity(body, MediaType.APPLICATION_JSON));
        try (Response r = response) {
            String actual = r.readEntity(String.class);
            dump(classpathResource, actual);
            ContentDiff.forJson(actual)
                    .expected(classpathResource)
                    .assertEquals();
        }
    }

    private static void dump(String classpathResource, String body) {
        java.nio.file.Path target = java.nio.file.Path.of(
                "target", "responses",
                classpathResource.substring(classpathResource.lastIndexOf('/') + 1));
        try {
            java.nio.file.Files.createDirectories(target.getParent());
            java.nio.file.Files.writeString(target, body);
        } catch (java.io.IOException ignored) {
            // Best-effort capture only.
        }
    }
}
