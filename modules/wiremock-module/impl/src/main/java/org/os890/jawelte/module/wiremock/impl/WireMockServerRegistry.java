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
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.os890.jawelte.module.wiremock.impl.adapter.extension.remap.WireMockManagedScope;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Per-test-class registry holding the per-endpoint
 * {@link EndpointResources} bundle (started {@code WireMockServer}
 * + matching {@code WireMock} stub client +
 * {@code WireMockRuntimeInfo} metadata view) for every discovered
 * {@code @WireMockEndpoint} qualifier — plus the single default
 * endpoint when no qualifier was discovered.
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
 * <p><b>Caching.</b> The {@code WireMock} client and the
 * {@code WireMockRuntimeInfo} are constructed exactly once per
 * endpoint — inside {@link #register(Class, WireMockServer)} via
 * {@link EndpointResources#from(WireMockServer)} — at the moment
 * {@code WireMockLifecycleAdapter} hands the started server over.
 * Every subsequent CDI injection point reads the cached instances
 * back; no producer method and no synthetic-bean
 * {@code produceWith} function constructs fresh
 * {@code WireMock} / {@code WireMockRuntimeInfo} objects on
 * lookup.
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

    private final ConcurrentMap<Class<? extends Annotation>, EndpointResources> resources =
            new ConcurrentHashMap<>();

    /** No-arg constructor required by the CDI runtime. */
    public WireMockServerRegistry() {
    }

    /**
     * Register a started {@code WireMockServer} under the given
     * endpoint key, building and caching the matching
     * {@code WireMock} client and {@code WireMockRuntimeInfo} on
     * the bundle in the same step. Idempotent: registering the
     * same key twice keeps the first bundle; the adapter never
     * starts duplicate servers for the same key.
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
        resources.putIfAbsent(endpointKey, EndpointResources.from(server));
    }

    /**
     * Look up the cached {@link EndpointResources} bundle for the
     * given endpoint key.
     *
     * @param endpointKey the qualifier annotation type, or
     *                    {@code jakarta.enterprise.inject.Default.class}
     *                    for the default endpoint
     * @return the cached bundle
     * @throws IllegalStateException if no bundle is registered for
     *                               the key (typically because
     *                               {@code beforeAll} hasn't run yet
     *                               or the key was never discovered)
     */
    public EndpointResources getFor(Class<? extends Annotation> endpointKey) {
        EndpointResources bundle = resources.get(endpointKey);
        if (bundle == null) {
            throw new IllegalStateException(
                    "No WireMockServer registered under endpoint key " + endpointKey.getName()
                            + ". The lifecycle adapter starts servers in beforeAll; "
                            + "lookup before that returns no result.");
        }
        return bundle;
    }

    /**
     * Look up the default endpoint's bundle — the one registered
     * under {@code jakarta.enterprise.inject.Default.class} when no
     * {@code @WireMockEndpoint} qualifier was discovered.
     *
     * @return the default {@link EndpointResources} bundle
     * @throws IllegalStateException when default-only mode is not
     *                               active (e.g. qualifiers were
     *                               discovered so synthetic beans
     *                               drive resolution instead)
     */
    public EndpointResources defaultEndpoint() {
        return getFor(Default.class);
    }

    /**
     * The full map of {@code (endpointKey, server)} entries — an
     * unmodifiable view derived from the underlying bundle map.
     * Used by external observers that want to inspect what was
     * registered without caring about the cached
     * {@code WireMock} / {@code WireMockRuntimeInfo}.
     *
     * @return an unmodifiable {@code Map} view
     */
    public Map<Class<? extends Annotation>, WireMockServer> entries() {
        return Collections.unmodifiableMap(
                resources.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().server())));
    }

    /**
     * The full set of registered {@code WireMockServer} instances.
     * Order is unspecified — callers that care about order must
     * sort by their preferred key after retrieval.
     *
     * @return an unmodifiable view of the registered servers
     */
    public Collection<WireMockServer> allServers() {
        return resources.values().stream()
                .map(EndpointResources::server)
                .toList();
    }

    /**
     * Remove every registered entry. Called by
     * {@code WireMockLifecycleAdapter.afterAll} after all servers
     * have been stopped. Does not stop servers — that's the
     * caller's responsibility.
     */
    public void clear() {
        resources.clear();
    }
}
