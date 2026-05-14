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
package org.os890.jawelte.tests.jaxrs.scenario15;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jaxrs.api.TestUrl;

/**
 * Scenario 15 — {@code TestUrl.get()} called before
 * {@code JaxRsLifecycleAdapter.beforeAll} has populated
 * {@code TestUrlHolder.baseUrl}. The test class carries
 * {@code @EnableTestBeans} (so the CDI container is up and
 * {@link TestUrl} is injectable) but NOT {@code @EnableJaxRs}, so
 * the lifecycle adapter never boots the server and the URL
 * holder's {@code baseUrl} stays {@code null}. Calling
 * {@code get()} on that state surfaces
 * {@code IllegalStateException("JAX-RS server not started yet")} —
 * the documented "called too early" contract.
 */
@EnableTestBeans
class Scenario15Test {

    @Inject
    private TestUrl testUrl;

    @Test
    void getBeforeServerStartedRaisesIllegalStateException() {
        assertThatThrownBy(() -> testUrl.get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JAX-RS server not started yet");
    }
}
