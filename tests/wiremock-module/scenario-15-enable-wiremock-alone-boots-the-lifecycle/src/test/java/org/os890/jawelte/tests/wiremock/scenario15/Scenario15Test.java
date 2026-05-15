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
package org.os890.jawelte.tests.wiremock.scenario15;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Scenario 15 — {@code @EnableWireMock} alone is sufficient to
 * activate the full lifecycle. The annotation is meta-annotated
 * with {@link EnableTestBeans}, so JUnit Jupiter walks the
 * meta-annotation chain and registers jawelte's proxy extension
 * even though the test class itself only carries
 * {@code @EnableWireMock}.
 *
 * <p>Verified by injecting a {@link WireMockServer} and observing
 * the server is up — same end-state as scenario 01, but
 * deliberately omitting the {@code @EnableTestBeans} declaration
 * to lock in the meta-annotation convenience.
 *
 * <p>(Repurposed from the original ticket scenario 15
 * "{@code @EnableWireMock} without {@code @EnableTestBeans} →
 * IllegalStateException"; the meta-annotation design makes that
 * failure mode unreachable.)
 */
@EnableWireMock
class Scenario15Test {

    @Inject
    private WireMockServer server;

    @Test
    void enableWireMockAloneBootsTheLifecycle() {
        assertThat(server)
                .as("@EnableWireMock alone activated the CDI machinery via its @EnableTestBeans meta-annotation")
                .isNotNull();
        assertThat(server.port())
                .as("the lifecycle adapter started the default server on an OS-assigned port")
                .isGreaterThan(0);
    }
}
