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
package org.os890.jawelte.module.cdi.api.port;

import org.os890.jawelte.core.api.port.TestContext;

/**
 * Boots and shuts down the CDI bean container around a test class.
 * The default implementation in {@code cdi-module/impl}
 * ({@code SeContainerCdiContainerPort}) wraps
 * {@code SeContainerInitializer} and is selected automatically when
 * cdi-module is the only CDI-bootstrapping module on the classpath.
 * A future Quarkus integration ships its own {@code CdiContainerPort}
 * implementation that drives Arc instead and wins via a lower
 * {@code @Priority} value.
 *
 * <p>Discovered via {@code ServiceLoader} and selected by
 * {@link TestContext#loadService(Class)}, which routes the priority
 * sort through the active
 * {@link org.os890.jawelte.core.api.port.ServicePriorityResolver}.
 * Exactly one implementation participates per test class; the
 * resolved instance is reused across {@link #start(TestContext)} and
 * {@link #stop(TestContext)}.
 *
 * <p>Implementations are expected to bind any container-handle
 * metadata they need on the {@link TestContext} during
 * {@code start} (e.g. the SE impl binds the {@code SeContainer}
 * reference under {@code SeContainer.class}) and unbind / close it
 * during {@code stop}. {@code CdiTestBeanContainer} owns the
 * lifecycle calls into this port; everything else (firing
 * {@code ContainerStarted}, activating the request scope) stays in
 * {@code CdiTestBeanContainer} so it does not have to be reimplemented
 * per CDI flavour.
 */
public interface CdiContainerPort {

    /**
     * Boot the bean container and bind any container-handle metadata
     * on the supplied {@link TestContext}. Called from
     * {@code CdiTestBeanContainer.beforeAll(...)} only when
     * {@code @EnableTestBeans(manageContainer=true)} (the default).
     *
     * @param testContext the per-test-class context; the
     *                    implementation binds whatever handle it
     *                    needs (e.g. {@code SeContainer}) here
     */
    void start(TestContext testContext);

    /**
     * Shut down the bean container previously booted by
     * {@link #start(TestContext)} and unbind its metadata from
     * {@code testContext}. Called from
     * {@code CdiTestBeanContainer.afterAll(...)}.
     *
     * @param testContext the per-test-class context whose bound
     *                    handle is read back, closed, and unbound
     */
    void stop(TestContext testContext);
}
