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
package org.os890.jawelte.tests.jaxrs.scenario06;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jaxrs.api.EnableJaxRs;
import org.os890.jawelte.module.jaxrs.api.TestUrl;

/**
 * Scenario 06 — verifies the
 * {@code @SessionScoped → @TestMethodScoped} remap end-to-end:
 *
 * <ul>
 *   <li>Method 1 increments the counter twice; the second
 *       increment observes the first (counts 1 then 2) — proves
 *       the bean instance survives across two HTTP requests
 *       within a single test method.</li>
 *   <li>Method 2 (executed after method 1 thanks to JUnit's
 *       {@code @Order}) increments once and reads 1 — proves the
 *       bean was reset between methods (fresh
 *       {@code @TestMethodScoped} instance).</li>
 * </ul>
 *
 * <p>The {@code @Order} matters only for reading the assertion
 * direction; the per-method reset works regardless of order
 * (each method gets its own {@code @TestMethodScoped} store via
 * scope-module's {@code ScopeLifecycleAdapter.beforeEach}).
 */
@EnableTestBeans
@EnableJaxRs(restResources = {Scenario06CounterResource.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Scenario06Test {

    @Inject
    private TestUrl testUrl;

    @Test
    @Order(1)
    void firstMethodAccumulatesAcrossTwoRequests() {
        assertThat(incrementOnce())
                .as("first increment within method 1 sees count 1")
                .isEqualTo("1");
        assertThat(incrementOnce())
                .as("second increment within method 1 sees count 2 (same instance survived)")
                .isEqualTo("2");
    }

    @Test
    @Order(2)
    void secondMethodStartsWithFreshCounter() {
        assertThat(incrementOnce())
                .as("first increment within method 2 sees count 1 (fresh @TestMethodScoped instance)")
                .isEqualTo("1");
    }

    private String incrementOnce() {
        try (Client client = ClientBuilder.newClient()) {
            try (Response response = client
                    .target(testUrl.get() + "/counter")
                    .request()
                    .get()) {
                assertThat(response.getStatus()).isEqualTo(200);
                return response.readEntity(String.class);
            }
        }
    }
}
