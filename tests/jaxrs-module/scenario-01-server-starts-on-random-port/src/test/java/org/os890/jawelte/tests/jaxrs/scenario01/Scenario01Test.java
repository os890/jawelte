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
package org.os890.jawelte.tests.jaxrs.scenario01;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jaxrs.api.EnableJaxRs;
import org.os890.jawelte.module.jaxrs.api.TestUrl;

/**
 * Scenario 01 — embedded {@code SeBootstrap} server boots on port
 * 0 (OS-assigned). After {@code beforeAll} returns, the
 * {@link TestUrl} bean has a non-null
 * {@code "http://localhost:{port}"} URL with a strictly positive
 * port number.
 *
 * <p>This is the smoke test of the jaxrs-module lifecycle adapter:
 * the assertion does not hit the HTTP endpoint, only verifies that
 * the server bootstrap completed and the URL holder was populated.
 * Subsequent scenarios exercise actual HTTP dispatch.
 */
@EnableTestBeans
@EnableJaxRs(restResources = {Scenario01HelloResource.class})
class Scenario01Test {

    @Inject
    private TestUrl testUrl;

    @Test
    void serverIsRunningOnOsAssignedPort() {
        String baseUrl = testUrl.get();
        assertThat(baseUrl)
                .as("TestUrl.get() returns a localhost URL after SeBootstrap.start completes")
                .startsWith("http://localhost:");
        URI uri = URI.create(baseUrl);
        assertThat(uri.getPort())
                .as("OS-assigned port is strictly positive (never 0, never -1)")
                .isGreaterThan(0);
    }
}
