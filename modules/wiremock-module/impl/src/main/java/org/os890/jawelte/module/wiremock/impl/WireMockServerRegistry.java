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

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.os890.jawelte.module.wiremock.impl.adapter.extension.remap.WireMockManagedScope;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Per-test-class registry holding the {@link WireMockServer}
 * instance for every discovered {@code @WireMockEndpoint} qualifier
 * — plus the single default endpoint when no qualifier was
 * discovered.
 *
 * <p><b>Scope.</b> Declared {@link ApplicationScoped}; carries the
 * impl-internal {@link WireMockManagedScope} marker so
 * {@code WireMockRegistryScopeRemap} (the
 * {@code AnnotationScopeRemap} provider this module ships) remaps
 * the scope to {@code @TestClassScoped} at
 * {@code ProcessAnnotatedType} time — driven by scope-module's
 * {@code ScopeRemapCdiExtension}. The effective lifetime is
 * per-test-class, matching the {@code WireMockServer} lifetime
 * managed by {@code WireMockLifecycleAdapter}.
 *
 * <p><b>Keying.</b> Servers are keyed by the qualifier annotation
 * <em>type</em> — not the annotation instance. CDI qualifier
 * equality compares attribute values, but every
 * {@code @WireMockEndpoint}-stamped user qualifier in the test
 * class hierarchy is treated as a distinct endpoint identity by
 * its type alone. The default endpoint (no qualifier discovered)
 * registers under {@code jakarta.enterprise.inject.Default.class}.
 *
 * <p><b>Thread safety.</b> Backed by a {@link ConcurrentHashMap}
 * (an impl-side concurrent collection, not exposed on the type
 * signature per the project's "api/spi exposes
 * {@code Map}, not {@code ConcurrentMap}" rule). Populated by
 * {@code WireMockLifecycleAdapter.beforeAll}, read by the per-
 * qualifier synthetic beans (and the default-only producer) at
 * injection-resolution time, mutated only by the adapter's
 * {@code afterAll} cleanup.
 */
@ApplicationScoped
@WireMockManagedScope
public class WireMockServerRegistry {

    private final ConcurrentMap<Class<? extends Annotation>, WireMockServer> servers =
            new ConcurrentHashMap<>();

    /** No-arg constructor required by the CDI runtime. */
    public WireMockServerRegistry() {
    }

    /**
     * Register a started {@code WireMockServer} under the given
     * endpoint key. Idempotent: registering the same key twice
     * keeps the first server; the adapter never starts duplicate
     * servers for the same key.
     *
     * @param endpointKey the qualifier annotation type identifying
     *                    the endpoint (or
     *                    {@code jakarta.enterprise.inject.Default.class}
     *                    for the default endpoint); must not be
     *                    {@code null}
     * @param server      the started {@code WireMockServer}; must
     *                    not be {@code null}
     */
    public void register(Class<? extends Annotation> endpointKey, WireMockServer server) {
        servers.putIfAbsent(endpointKey, server);
    }

    /**
     * Look up the {@code WireMockServer} registered under the given
     * endpoint key.
     *
     * @param endpointKey the qualifier annotation type, or
     *                    {@code jakarta.enterprise.inject.Default.class}
     *                    for the default endpoint
     * @return the registered server
     * @throws IllegalStateException if no server is registered for
     *                               the key (typically because
     *                               {@code beforeAll} hasn't run yet
     *                               or the key was never discovered)
     */
    public WireMockServer getFor(Class<? extends Annotation> endpointKey) {
        WireMockServer server = servers.get(endpointKey);
        if (server == null) {
            throw new IllegalStateException(
                    "No WireMockServer registered under endpoint key " + endpointKey.getName()
                            + ". The lifecycle adapter starts servers in beforeAll; "
                            + "lookup before that returns no result.");
        }
        return server;
    }

    /**
     * Look up the default endpoint's {@code WireMockServer} — the
     * one registered under
     * {@code jakarta.enterprise.inject.Default.class} when no
     * {@code @WireMockEndpoint} qualifier was discovered.
     *
     * @return the default {@code WireMockServer}
     * @throws IllegalStateException when default-only mode is not
     *                               active (e.g. qualifiers were
     *                               discovered so synthetic beans
     *                               drive resolution instead)
     */
    public WireMockServer defaultServer() {
        return getFor(Default.class);
    }

    /**
     * The full map of {@code (endpointKey, server)} entries — an
     * unmodifiable view backed by the underlying concurrent map.
     * Used by {@code WireMockLifecycleAdapter} to iterate servers
     * for {@code resetAll()} in {@code beforeEach} and for
     * {@code stop()} in {@code afterAll}.
     *
     * @return an unmodifiable {@code Map} view
     */
    public Map<Class<? extends Annotation>, WireMockServer> entries() {
        return Collections.unmodifiableMap(servers);
    }

    /**
     * The full set of registered {@code WireMockServer} instances.
     * Order is unspecified — callers that care about order must
     * sort by their preferred key after retrieval.
     *
     * @return an unmodifiable view of the registered servers
     */
    public Collection<WireMockServer> allServers() {
        return Collections.unmodifiableCollection(servers.values());
    }

    /**
     * Remove every registered entry. Called by
     * {@code WireMockLifecycleAdapter.afterAll} after all servers
     * have been stopped. Does not stop servers — that's the
     * caller's responsibility.
     */
    public void clear() {
        servers.clear();
    }
}
