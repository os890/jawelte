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
package org.os890.jawelte.tests.wiremock.scenario06;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Scenario 06 — meta-annotation discovery. A field annotated with
 * {@link PaymentService} (itself meta-annotated with
 * {@link PaymentApi}, which carries
 * {@code @WireMockEndpoint(port=18091)}) resolves to a
 * {@code WireMockServer} bound to port 18091. The extension's
 * recursive annotation walk discovers the endpoint and registers a
 * synthetic bean qualified with {@code @PaymentService}.
 */
@EnableWireMock
class Scenario06Test {

    @Inject
    @PaymentService
    private WireMockServer paymentServer;

    @Test
    void metaAnnotatedQualifierResolvesToEndpoint() {
        assertThat(paymentServer.port())
                .as("recursive scan: PaymentService → PaymentApi → @WireMockEndpoint(port=18091)")
                .isEqualTo(18091);
    }
}
