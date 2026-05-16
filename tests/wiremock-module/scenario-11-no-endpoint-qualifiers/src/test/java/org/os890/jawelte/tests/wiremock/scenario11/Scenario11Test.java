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
package org.os890.jawelte.tests.wiremock.scenario11;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;
import org.os890.jawelte.module.wiremock.impl.WireMockServerRegistry;

/**
 * Scenario 11 — verifies the lifecycle adapter starts exactly one
 * default {@code WireMockServer} in default-only mode (no
 * {@code @WireMockEndpoint} qualifier anywhere in the test class
 * hierarchy). Asserts on the registry's size directly to make the
 * "exactly one default endpoint" contract explicit.
 */
@EnableWireMock
class Scenario11Test {

    @Inject
    private WireMockServerRegistry registry;

    @Test
    void exactlyOneDefaultServerWasRegistered() {
        assertThat(registry.entries())
                .as("default-only mode: one entry under Default.class")
                .hasSize(1);
        assertThat(registry.entries().keySet().iterator().next())
                .as("the sole entry is keyed by jakarta.enterprise.inject.Default")
                .isEqualTo(jakarta.enterprise.inject.Default.class);
    }
}
