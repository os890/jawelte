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
package org.os890.jawelte.tests.wiremock.scenario17;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.os890.jawelte.module.scope.api.TestClassScoped;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * {@link TestClassScoped @TestClassScoped} bean that captures
 * the injected {@code WireMockServer} reference and probes it
 * in {@code @PreDestroy}.
 *
 * <p>scope-module deactivates the {@code @TestClassScoped}
 * context in its own {@code afterAll}
 * ({@code @Priority(100)}); the deactivation runs
 * {@code @PreDestroy} on every contextual bean. The probe
 * inside {@code @PreDestroy} therefore observes the
 * {@code WireMockServer} state right at that moment in the
 * adapter chain. wiremock-module's {@code afterAll} runs
 * <em>after</em> scope-module's (LIFO from priority 75 vs 100),
 * so the probe must see {@code isRunning() == true}.
 */
@TestClassScoped
public class Scenario17ScopeObserver {

    @Inject
    private WireMockServer server;

    /** No-arg constructor required by the CDI runtime. */
    public Scenario17ScopeObserver() {
    }

    /**
     * Forces the bean to be instantiated when the test method
     * touches it — without a method call, CDI would defer
     * proxy resolution and the underlying contextual instance
     * would never be created, so {@code @PreDestroy} would never
     * fire.
     */
    public void touch() {
        // No-op — exists only to force lazy CDI instantiation.
    }

    @PreDestroy
    void onPreDestroy() {
        Scenario17Recorder.PRE_DESTROY_INVOKED.set(true);
        Scenario17Recorder.SERVER_RUNNING_AT_PRE_DESTROY.set(server.isRunning());
    }
}
