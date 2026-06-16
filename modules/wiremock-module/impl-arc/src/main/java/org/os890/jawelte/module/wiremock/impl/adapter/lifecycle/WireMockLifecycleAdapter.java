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
package org.os890.jawelte.module.wiremock.impl.adapter.lifecycle;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Qualifier;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;
import org.os890.jawelte.module.wiremock.api.WireMockEndpoint;
import org.os890.jawelte.module.wiremock.impl.WireMockServerRegistry;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;

/**
 * {@link TestModuleLifecyclePort} adapter shipped by
 * wiremock-module/impl. Starts {@code WireMockServer} instances in
 * {@code beforeAll}, resets stubs via
 * {@code WireMockServer.resetAll()} in {@code beforeEach}, and
 * stops the servers in {@code afterAll}.
 *
 * <p><b>Priority.</b> {@code @Priority(75)} — same band as
 * jaxrs-module, between testcontrol-module (50) and scope-module
 * (100). Servers are ready by the time scopes activate; torn down
 * in LIFO order after scopes deactivate but before cdi-module
 * closes the {@code SeContainer}. The relative ordering between
 * wiremock-module and jaxrs-module at the same priority is
 * undefined; the two are independent.
 *
 * <p><b>Trigger.</b> The adapter is a no-op for test classes that
 * are not annotated {@link EnableWireMock}. When present, the
 * adapter scans the test class hierarchy directly for
 * {@code @Inject}-able WireMock fields and walks each field's
 * qualifier hierarchy looking for an
 * {@code @WireMockEndpoint}-rooted user qualifier — the same logic
 * {@code WireMockCdiExtension.onBeforeBeanDiscovery} runs under
 * OWB/Weld, kept locally so the adapter does not depend on a
 * portable extension being reachable through ArC's
 * {@code BeanManager} (which rejects
 * {@code getExtension(...)} with
 * {@code UnsupportedOperationException}). Empty discovery → start
 * one default endpoint on port 0 and register under
 * {@code jakarta.enterprise.inject.Default.class}. Non-empty
 * discovery → iterate unique endpoint keys (the values of the
 * discovery map), read the port off each key's
 * {@code @WireMockEndpoint} meta-annotation, and start one server
 * per key.
 *
 * <p><b>Start failure recovery.</b> If a {@code start()} call
 * throws after one or more earlier servers have already started,
 * the already-started servers are stopped (best-effort, suppressing
 * any individual stop failure) before the original exception
 * propagates. TICKET-001 does not call {@code afterAll} for the
 * failing adapter, so this self-cleanup is the only way to release
 * the OS ports.
 *
 * <p><b>Cleanup.</b> {@code afterAll} stops every registered
 * server in registration order. Stop failures are collected and
 * the first failure is rethrown with the remaining failures
 * attached as suppressed exceptions; the registry is cleared in a
 * {@code finally} block regardless.
 */
@Priority(75)
public class WireMockLifecycleAdapter implements TestModuleLifecyclePort {

    /** No-arg constructor required by SPI {@code ServiceLoader} lookup. */
    public WireMockLifecycleAdapter() {
    }

    private static final Set<Class<?>> INJECTABLE_WIREMOCK_TYPES = Set.of(
            WireMockServer.class,
            WireMock.class,
            WireMockRuntimeInfo.class);

    @Override
    public void beforeAll(TestContext testContext) {
        Class<?> testClass = testContext.getTestClass();
        if (testClass.getAnnotation(EnableWireMock.class) == null) {
            return;
        }
        WireMockServerRegistry registry = CDI.current().select(WireMockServerRegistry.class).get();
        // Under ArC, BeanManager.getExtension(...) is unsupported, so
        // discover the @WireMockEndpoint qualifier hierarchy directly
        // off the test class's @Inject fields. Mirrors the logic
        // WireMockCdiExtension.onBeforeBeanDiscovery runs under
        // OWB/Weld, kept locally so the adapter doesn't depend on the
        // portable extension being reachable through CDI.
        Map<Class<? extends Annotation>, Class<? extends Annotation>> endpoints =
                discoverEndpoints(testClass);
        Set<Class<? extends Annotation>> uniqueKeys = new LinkedHashSet<>(endpoints.values());

        List<WireMockServer> started = new ArrayList<>();
        try {
            if (uniqueKeys.isEmpty()) {
                WireMockServer defaultServer = startServer(0);
                started.add(defaultServer);
                registry.register(Default.class, defaultServer);
            } else {
                for (Class<? extends Annotation> endpointKey : uniqueKeys) {
                    int port = endpointKey.getAnnotation(WireMockEndpoint.class).port();
                    WireMockServer server = startServer(port);
                    started.add(server);
                    registry.register(endpointKey, server);
                }
            }
        } catch (RuntimeException startFailure) {
            stopBestEffort(started, startFailure);
            throw startFailure;
        }
        testContext.bindMetadata(StartedWireMockServers.class,
                new StartedWireMockServers(List.copyOf(started)));
    }

    @Override
    public void beforeEach(TestContext testContext) {
        Class<?> testClass = testContext.getTestClass();
        if (testClass.getAnnotation(EnableWireMock.class) == null) {
            return;
        }
        WireMockServerRegistry registry = CDI.current().select(WireMockServerRegistry.class).get();
        for (WireMockServer server : registry.allServers()) {
            server.resetAll();
        }
    }

    @Override
    public void afterAll(TestContext testContext) {
        Class<?> testClass = testContext.getTestClass();
        if (testClass.getAnnotation(EnableWireMock.class) == null) {
            return;
        }
        List<WireMockServer> servers = testContext.getMetadata(StartedWireMockServers.class)
                .map(StartedWireMockServers::servers)
                .orElseGet(List::of);
        RuntimeException aggregate = null;
        try {
            for (WireMockServer server : servers) {
                try {
                    server.stop();
                } catch (RuntimeException stopFailure) {
                    if (aggregate == null) {
                        aggregate = stopFailure;
                    } else {
                        aggregate.addSuppressed(stopFailure);
                    }
                }
            }
        } finally {
            testContext.unbindMetadata(StartedWireMockServers.class);
        }
        if (aggregate != null) {
            throw aggregate;
        }
    }

    /**
     * Walks the test class hierarchy and returns a
     * {@code userQualifierType → endpointKey} map of every
     * {@code @WireMockEndpoint}-rooted qualifier reachable from a
     * field whose type is one of the injectable WireMock types.
     * Mirrors {@code WireMockCdiExtension.onBeforeBeanDiscovery} so
     * the adapter does not have to read it through
     * {@code BeanManager.getExtension(...)} — which ArC's
     * {@code BeanManagerImpl} rejects with
     * {@code UnsupportedOperationException}.
     *
     * @param testClass the active test class
     * @return the discovered map (insertion-ordered); empty in the
     *         default-only case
     */
    private static Map<Class<? extends Annotation>, Class<? extends Annotation>>
            discoverEndpoints(Class<?> testClass) {
        Map<Class<? extends Annotation>, Class<? extends Annotation>> sink = new LinkedHashMap<>();
        for (Class<?> current = testClass; current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!INJECTABLE_WIREMOCK_TYPES.contains(field.getType())) {
                    continue;
                }
                for (Annotation annotation : field.getAnnotations()) {
                    collectEndpoint(annotation.annotationType(), sink, new HashSet<>());
                }
            }
        }
        return sink;
    }

    private static void collectEndpoint(
            Class<? extends Annotation> annotationType,
            Map<Class<? extends Annotation>, Class<? extends Annotation>> sink,
            Set<Class<? extends Annotation>> visited) {
        if (!visited.add(annotationType)) {
            return;
        }
        Class<? extends Annotation> endpointKey = findEndpointAncestor(annotationType, new HashSet<>());
        if (endpointKey == null) {
            return;
        }
        if (annotationType.isAnnotationPresent(Qualifier.class)) {
            sink.putIfAbsent(annotationType, endpointKey);
        }
    }

    private static Class<? extends Annotation> findEndpointAncestor(
            Class<? extends Annotation> annotationType, Set<Class<? extends Annotation>> visited) {
        if (!visited.add(annotationType)) {
            return null;
        }
        if (annotationType.isAnnotationPresent(WireMockEndpoint.class)) {
            return annotationType;
        }
        for (Annotation meta : annotationType.getAnnotations()) {
            String packageName = meta.annotationType().getPackageName();
            if (packageName.startsWith("java.lang") || packageName.startsWith("jakarta.")) {
                continue;
            }
            Class<? extends Annotation> result = findEndpointAncestor(meta.annotationType(), visited);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static WireMockServer startServer(int port) {
        WireMockConfiguration configuration = WireMockConfiguration.wireMockConfig();
        if (port == 0) {
            configuration.dynamicPort();
        } else {
            configuration.port(port);
        }
        WireMockServer server = new WireMockServer(configuration);
        server.start();
        return server;
    }

    private static void stopBestEffort(List<WireMockServer> started, RuntimeException originalFailure) {
        for (WireMockServer server : started) {
            try {
                server.stop();
            } catch (RuntimeException stopFailure) {
                originalFailure.addSuppressed(stopFailure);
            }
        }
    }

    /**
     * Per-test-class metadata bound on {@code TestContext} during
     * {@code beforeAll} and read back during {@code afterAll}.
     * Carries the list of {@link WireMockServer} instances this
     * adapter started so the {@code stop()} loop is decoupled
     * from the per-test-class {@code WireMockServerRegistry}
     * bean's lifecycle — scope-module's {@code afterAll}
     * (priority 100) deactivates {@code @TestClassScoped} BEFORE
     * wiremock-module's {@code afterAll} (priority 75) runs in
     * LIFO order, so by that point the registry's contextual
     * instance has been destroyed and a fresh read would see no
     * servers. The metadata list lives on {@code TestContext}
     * which outlives every {@code TestModuleLifecyclePort.afterAll}
     * — same survival window as the captured-reference assertion
     * in scenario 10.
     *
     * @param servers the started servers; defensively copied to
     *                immutable form on construction
     */
    public record StartedWireMockServers(List<WireMockServer> servers) {

        /** Defensive-copy compact constructor. */
        public StartedWireMockServers {
            servers = List.copyOf(servers);
        }
    }
}
