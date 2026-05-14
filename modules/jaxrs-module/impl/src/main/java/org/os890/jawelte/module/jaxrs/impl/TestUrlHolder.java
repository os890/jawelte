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
package org.os890.jawelte.module.jaxrs.impl;

import jakarta.enterprise.context.ApplicationScoped;

import org.os890.jawelte.module.jaxrs.api.TestUrl;

/**
 * CDI bean implementation of {@link TestUrl}. Stores the embedded
 * server's resolved base URL as a {@code volatile} string set by
 * {@code JaxRsLifecycleAdapter} after {@code SeBootstrap.start}
 * completes; {@link #get()} is a single field read after that.
 *
 * <p><b>Scope.</b> Declared {@link ApplicationScoped}. When
 * testcontrol-module is on the classpath, {@code JaxRsCdiExtension}
 * rewrites the effective scope to {@code @TestClassScoped} at
 * {@code ProcessAnnotatedType} time — the bean's lifetime then
 * matches the per-test-class server lifetime exactly. Under
 * cdi-module's per-test-class CDI container the two scopes are
 * observably equivalent (one URL per test class either way), so
 * the upgrade is a cosmetic guarantee for the rare case where a
 * downstream consumer ships a per-method CDI container.
 *
 * <p><b>State.</b> One {@code volatile String}. Set by the lifecycle
 * adapter in {@code beforeAll} (after the OS-assigned port is
 * resolved) and cleared in {@code afterAll}; never mutated by user
 * code. {@code volatile} guarantees that JAX-RS worker threads
 * reading {@link #get()} during HTTP dispatch see the current value
 * regardless of which thread bound it.
 */
@ApplicationScoped
public class TestUrlHolder implements TestUrl {

    private volatile String baseUrl;

    /** No-arg constructor required by the CDI runtime. */
    public TestUrlHolder() {
    }

    /**
     * Publish the resolved base URL. Called by
     * {@code JaxRsLifecycleAdapter.beforeAll} once
     * {@code SeBootstrap.start} returns the
     * {@code SeBootstrap.Instance} with an OS-assigned port.
     *
     * @param baseUrl the resolved {@code "http://localhost:{port}"}
     *                URL; must not be {@code null}
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Clear the published base URL. Called by
     * {@code JaxRsLifecycleAdapter.afterAll} after the server is
     * stopped; restores the "not started yet" state so subsequent
     * {@link #get()} calls fail loudly instead of returning a stale
     * URL.
     */
    public void clear() {
        this.baseUrl = null;
    }

    @Override
    public String get() {
        String snapshot = baseUrl;
        if (snapshot == null) {
            throw new IllegalStateException("JAX-RS server not started yet");
        }
        return snapshot;
    }
}
