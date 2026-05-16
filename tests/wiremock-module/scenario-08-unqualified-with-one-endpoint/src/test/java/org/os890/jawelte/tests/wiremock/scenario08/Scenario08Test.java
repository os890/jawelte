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
package org.os890.jawelte.tests.wiremock.scenario08;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Scenario 08 — single endpoint, mixed qualified + unqualified
 * injection. The synthetic bean registered for {@code @PaymentApi}
 * carries {@code @Default} as well, so an unqualified
 * {@code @Inject WireMockServer} resolves to it. Both injections
 * are backed by the same registry entry, hence the same
 * {@link WireMockServer} instance.
 */
@EnableWireMock
class Scenario08Test {

    @Inject
    @PaymentApi
    private WireMockServer qualifiedServer;

    @Inject
    private WireMockServer unqualifiedServer;

    @Test
    void unqualifiedInjectionResolvesToTheSingleEndpoint() {
        assertThat(unqualifiedServer)
                .as("the synthetic bean's @Default qualifier covers unqualified injection in single-endpoint mode")
                .isSameAs(qualifiedServer);
    }
}
