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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;

/**
 * Per-endpoint bundle held in {@link WireMockServerRegistry}.
 * Built once when {@code WireMockLifecycleAdapter.beforeAll}
 * registers a started {@link WireMockServer}; the matching
 * {@link WireMock} stub client and the upstream
 * {@link WireMockRuntimeInfo} metadata view are constructed at
 * the same moment and cached on the bundle. Subsequent CDI
 * injection points (the producer's {@code @Default @Produces}
 * methods in {@link WireMockProducer} and the per-qualifier
 * synthetic beans registered by
 * {@code WireMockCdiExtension.onAfterBeanDiscovery}) read the
 * cached instances back out — they do <b>not</b> construct fresh
 * {@code WireMock} / {@code WireMockRuntimeInfo} objects per
 * injection point.
 *
 * <p>Records are intentional here: the bundle is immutable, the
 * three components are co-derived from a single
 * {@code WireMockServer}, and there is no behaviour worth
 * subclassing.
 *
 * @param server      the started {@link WireMockServer} for this
 *                    endpoint; never {@code null}
 * @param client      the {@link WireMock} stub-registration
 *                    client targeting {@code server}'s host + HTTP
 *                    port; never {@code null}
 * @param runtimeInfo the {@link WireMockRuntimeInfo} metadata
 *                    view built from {@code server}; never
 *                    {@code null}
 */
public record EndpointResources(
        WireMockServer server,
        WireMock client,
        WireMockRuntimeInfo runtimeInfo) {

    /**
     * Construct the bundle from a started {@link WireMockServer}.
     * Called by {@link WireMockServerRegistry#register} once per
     * endpoint, immediately after
     * {@code WireMockLifecycleAdapter} starts the server.
     *
     * @param server the started server; the resolved port is read
     *               here to bind the {@link WireMock} client and
     *               {@link WireMockRuntimeInfo}
     * @return a fresh {@code EndpointResources} bundle
     */
    public static EndpointResources from(WireMockServer server) {
        return new EndpointResources(
                server,
                new WireMock(server.port()),
                new WireMockRuntimeInfo(server));
    }
}
