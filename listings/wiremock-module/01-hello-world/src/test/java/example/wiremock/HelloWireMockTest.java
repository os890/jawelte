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
package example.wiremock;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;

/**
 * Minimal wiremock-module usage: @EnableWireMock boots a server,
 * the test injects WireMockRuntimeInfo and reads the live port / base
 * URL off it. Stub registration and HTTP calls are not needed for the
 * hello-world — the metadata bean is the smallest proof that the
 * container + producer wiring is in place.
 */
@EnableWireMock
class HelloWireMockTest {

    @Inject
    private WireMockRuntimeInfo runtimeInfo;

    @Test
    void runtimeInfoIsInjectedWithALiveBaseUrlAndPort() {
        assertThat(runtimeInfo.getHttpPort()).isPositive();
        assertThat(runtimeInfo.getHttpBaseUrl()).startsWith("http://localhost:");
    }
}
