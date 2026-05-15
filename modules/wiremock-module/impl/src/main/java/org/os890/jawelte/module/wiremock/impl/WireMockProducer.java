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
package org.os890.jawelte.module.wiremock.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * Default-endpoint producer — the api↔library bridge for the case
 * where no {@code @WireMockEndpoint}-stamped qualifier is
 * discovered in the test class hierarchy.
 *
 * <p>Both producer methods are {@link Default @Default}-qualified
 * and {@link Dependent @Dependent}-scoped, satisfying
 * {@code @Inject WireMockServer} and {@code @Inject WireMock}
 * without a qualifier on the injection point. The methods read the
 * single default {@code WireMockServer} off
 * {@link WireMockServerRegistry#defaultServer()} — which is
 * populated by {@code WireMockLifecycleAdapter.beforeAll}.
 *
 * <p><b>Vetoing.</b> {@code WireMockCdiExtension} vetoes this whole
 * producer class via {@code ProcessAnnotatedType.veto()} when at
 * least one {@code @WireMockEndpoint}-stamped qualifier was
 * discovered. In that case the synthetic beans registered in
 * {@code AfterBeanDiscovery} drive resolution (every synthetic
 * bean carries {@code @Default} plus the user qualifier, so
 * unqualified injection resolves to the single synthetic bean in
 * single-qualifier mode and raises
 * {@code AmbiguousResolutionException} in multi-qualifier mode).
 *
 * <p>This bean is itself {@code @ApplicationScoped} — only the
 * producer methods are {@code @Dependent}. The class scope is
 * irrelevant to user code (producer methods are looked up by
 * type+qualifier, not by holding a reference to the producer
 * bean); {@code @ApplicationScoped} is the cheapest scope that
 * keeps the injected {@link WireMockServerRegistry} resolution
 * fast (one CDI lookup per container).
 */
@ApplicationScoped
public class WireMockProducer {

    @Inject
    private WireMockServerRegistry registry;

    /** No-arg constructor required by the CDI runtime. */
    public WireMockProducer() {
    }

    /**
     * Produce the default endpoint's {@code WireMockServer}.
     *
     * @return the {@code WireMockServer} for the default endpoint
     */
    @Produces
    @Default
    @Dependent
    public WireMockServer produceWireMockServer() {
        return registry.defaultServer();
    }

    /**
     * Produce the default endpoint's
     * {@code com.github.tomakehurst.wiremock.client.WireMock} stub
     * registration client. The client targets the default server's
     * host + HTTP port.
     *
     * @return the {@code WireMock} client for the default endpoint
     */
    @Produces
    @Default
    @Dependent
    public WireMock produceWireMockClient() {
        WireMockServer server = registry.defaultServer();
        return new WireMock(server.port());
    }
}
