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
package org.os890.jawelte.tests.wiremock.scenario27;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;

import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * wiremock-module registers synthetic beans for {@code WireMockServer},
 * {@code WireMock} and {@code WireMockRuntimeInfo}. Unless it records
 * those types, cdi-module's auto-mocking registers a competing bean for
 * the same type and the deployment fails with
 * {@code AmbiguousResolutionException} — the #124 failure in another
 * module.
 *
 * <p>Of the four modules #129 lists, wiremock-module is the one whose
 * types no {@code framework-exclude-packages} contribution covers, so
 * this is where the record actually carries the weight. jpa-module,
 * jta-module and spring-data-module are already shielded by their
 * package prefixes — bluntly, but effectively.
 *
 * <p>The injection points are <b>qualified</b>. An unqualified
 * {@code @Inject WireMock} is satisfied by wiremock-module's own
 * {@code @Produces} producer, which auto-mock can see through
 * {@code getBeans(...)}, so it stands in for nothing and the collision
 * never arises. A qualified point has no visible producer — only the
 * synthetic bean, which auto-mock cannot see.
 *
 * <p>{@link StubMockFactory} replaces the shipped Mockito factory on
 * purpose: without it auto-mock produces nothing on this JDK (#128) and
 * this scenario would pass whatever the code did.
 */
@EnableWireMock
class Scenario27Test {

    @Inject
    private EndpointUser endpointUser;

    @Inject
    @PaymentApi
    private WireMock client;

    @Test
    void anApplicationBeanGetsTheRealClient() {
        assertThat(endpointUser.client()).isNotNull();
        assertThatCode(() -> endpointUser.client().resetMappings())
                .as("a plain @Inject WireMock must resolve to the client wiremock-module "
                        + "supplied, which reaches the running server; the stand-in points "
                        + "at a dead port and would fail here")
                .doesNotThrowAnyException();
    }

    @Test
    void theTestClassGetsTheRealClientToo() {
        assertThatCode(() -> client.resetMappings()).doesNotThrowAnyException();
    }

    @Test
    void theStubFactoryReallyWouldHaveProducedSomething() {
        assertThat(new StubMockFactory().create(WireMock.class))
                .as("if the factory refused this type, auto-mock would register nothing "
                        + "and the assertions above would pass with the record missing")
                .isNotNull();
    }
}
