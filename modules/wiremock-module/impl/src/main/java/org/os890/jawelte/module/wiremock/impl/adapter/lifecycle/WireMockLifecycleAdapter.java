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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;
import org.os890.jawelte.module.wiremock.api.WireMockEndpoint;
import org.os890.jawelte.module.wiremock.api.event.WireMockServersStopped;
import org.os890.jawelte.module.wiremock.impl.WireMockServerRegistry;
import org.os890.jawelte.module.wiremock.impl.adapter.extension.WireMockCdiExtension;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

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
 * adapter reads the discovered endpoints off the
 * {@link WireMockCdiExtension} via
 * {@code BeanManager.getExtension(...)}. Empty discovery → start
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

    @Override
    public void beforeAll(TestContext testContext) {
        Class<?> testClass = testContext.getTestClass();
        if (testClass.getAnnotation(EnableWireMock.class) == null) {
            return;
        }
        BeanManager beanManager = CDI.current().getBeanManager();
        WireMockServerRegistry registry = CDI.current().select(WireMockServerRegistry.class).get();
        WireMockCdiExtension extension = beanManager.getExtension(WireMockCdiExtension.class);
        Map<Class<? extends Annotation>, Class<? extends Annotation>> endpoints =
                extension.discoveredEndpoints();
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
        WireMockServerRegistry registry = CDI.current().select(WireMockServerRegistry.class).get();
        RuntimeException aggregate = null;
        try {
            for (WireMockServer server : registry.allServers()) {
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
            CDI.current().getBeanManager()
                    .getEvent()
                    .select(WireMockServersStopped.class)
                    .fire(new WireMockServersStopped());
        } finally {
            registry.clear();
        }
        if (aggregate != null) {
            throw aggregate;
        }
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
}
