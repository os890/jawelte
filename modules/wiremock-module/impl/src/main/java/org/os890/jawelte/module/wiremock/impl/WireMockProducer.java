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
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;

/**
 * Default-endpoint producer — the api↔library bridge for the case
 * where no {@code @WireMockEndpoint}-stamped qualifier is
 * discovered in the test class hierarchy.
 *
 * <p>All three producer methods are {@link Default @Default}-qualified
 * and {@link Dependent @Dependent}-scoped, satisfying
 * {@code @Inject WireMockServer}, {@code @Inject WireMock}, and
 * {@code @Inject WireMockRuntimeInfo} without a qualifier on the
 * injection point. The methods read the cached default
 * {@link EndpointResources} bundle off
 * {@link WireMockServerRegistry#defaultEndpoint()} — populated
 * by {@code WireMockLifecycleAdapter.beforeAll} when the server
 * was started; the {@code WireMock} client and the
 * {@code WireMockRuntimeInfo} are built exactly once at that point
 * and reused for every injection.
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
     * Produce the default endpoint's {@code WireMockServer} —
     * reads the cached bundle off
     * {@link WireMockServerRegistry#defaultEndpoint()}.
     *
     * @return the cached {@code WireMockServer} for the default
     *         endpoint
     */
    @Produces
    @Default
    @Dependent
    public WireMockServer produceWireMockServer() {
        return registry.defaultEndpoint().server();
    }

    /**
     * Produce the default endpoint's
     * {@code com.github.tomakehurst.wiremock.client.WireMock} stub
     * registration client — the cached instance on the
     * {@link EndpointResources} bundle, built once when the
     * server was registered.
     *
     * @return the cached {@code WireMock} client for the default
     *         endpoint
     */
    @Produces
    @Default
    @Dependent
    public WireMock produceWireMockClient() {
        return registry.defaultEndpoint().client();
    }

    /**
     * Produce the default endpoint's
     * {@code com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo}
     * — the upstream metadata view over the running server (HTTP /
     * HTTPS ports, base URLs, enabled flags, embedded
     * {@code WireMock} client). Returned from the cached
     * {@link EndpointResources} bundle, built once at server
     * registration.
     *
     * @return the cached {@code WireMockRuntimeInfo} for the
     *         default endpoint
     */
    @Produces
    @Default
    @Dependent
    public WireMockRuntimeInfo produceWireMockRuntimeInfo() {
        return registry.defaultEndpoint().runtimeInfo();
    }
}
