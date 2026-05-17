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
package org.os890.jawelte.tests.jaxrs.scenario20;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.jaxrs.api.EnableJaxRs;
import org.os890.jawelte.module.jaxrs.api.ResponseDiff;
import org.os890.jawelte.module.jaxrs.api.TestUrl;

/**
 * Scenario 20 — a single {@code @Test} method walks the full CRUD
 * lifecycle end-to-end against {@link Scenario20ItemResource}:
 *
 * <ol>
 *   <li>{@code GET /items} — empty list to start</li>
 *   <li>{@code POST /items?name=Alpha} — create the first row,
 *       server assigns id=1</li>
 *   <li>{@code POST /items?name=Beta} — create the second row,
 *       server assigns id=2</li>
 *   <li>{@code GET /items} — both rows visible</li>
 *   <li>{@code PUT /items/1?name=AlphaRenamed} — update one row</li>
 *   <li>{@code GET /items/1} — confirm the update</li>
 *   <li>{@code DELETE /items/1} — drop the updated row</li>
 *   <li>{@code GET /items} — only the second row remains</li>
 * </ol>
 *
 * <p>The test asserts every response body via
 * {@link ResponseDiff#forJson}; the resource is the simple in-memory
 * {@code ConcurrentHashMap} variant — no JPA, no DB, no transactions
 * — so the only thing under test is the CDI + jaxrs round-trip glue
 * across multiple HTTP verbs in a single test method.
 */
@EnableJaxRs(restResources = {Scenario20ItemResource.class})
class Scenario20Test {

    @Inject
    private TestUrl testUrl;

    @Test
    void fullCrudRoundtrip() {
        try (Client client = ClientBuilder.newClient()) {
            // 1. initial GET — empty list
            try (Response r = client.target(testUrl.get() + "/items")
                    .request().get()) {
                ResponseDiff.forJson(r).expectedContent("[]").assertEquals();
            }
            // 2. POST first item
            try (Response r = client.target(testUrl.get() + "/items")
                    .queryParam("name", "Alpha")
                    .request().post(Entity.entity("", MediaType.APPLICATION_JSON))) {
                ResponseDiff.forJson(r)
                        .expectedContent("{\"id\":1,\"name\":\"Alpha\"}")
                        .assertEquals();
            }
            // 3. POST second item
            try (Response r = client.target(testUrl.get() + "/items")
                    .queryParam("name", "Beta")
                    .request().post(Entity.entity("", MediaType.APPLICATION_JSON))) {
                ResponseDiff.forJson(r)
                        .expectedContent("{\"id\":2,\"name\":\"Beta\"}")
                        .assertEquals();
            }
            // 4. GET all — both rows
            try (Response r = client.target(testUrl.get() + "/items")
                    .request().get()) {
                ResponseDiff.forJson(r).expectedContent(
                        "[{\"id\":1,\"name\":\"Alpha\"},"
                                + "{\"id\":2,\"name\":\"Beta\"}]")
                        .assertEquals();
            }
            // 5. PUT id=1
            try (Response r = client.target(testUrl.get() + "/items/1")
                    .queryParam("name", "AlphaRenamed")
                    .request().put(Entity.entity("", MediaType.APPLICATION_JSON))) {
                ResponseDiff.forJson(r)
                        .expectedContent("{\"id\":1,\"name\":\"AlphaRenamed\"}")
                        .assertEquals();
            }
            // 6. GET id=1 — confirm update
            try (Response r = client.target(testUrl.get() + "/items/1")
                    .request().get()) {
                ResponseDiff.forJson(r)
                        .expectedContent("{\"id\":1,\"name\":\"AlphaRenamed\"}")
                        .assertEquals();
            }
            // 7. DELETE id=1
            try (Response r = client.target(testUrl.get() + "/items/1")
                    .request().delete()) {
                ResponseDiff.forJson(r)
                        .expectedContent("{\"deletedId\":1}")
                        .assertEquals();
            }
            // 8. final GET — only id=2 remains
            try (Response r = client.target(testUrl.get() + "/items")
                    .request().get()) {
                ResponseDiff.forJson(r)
                        .expectedContent("[{\"id\":2,\"name\":\"Beta\"}]")
                        .assertEquals();
            }
        }
    }
}
