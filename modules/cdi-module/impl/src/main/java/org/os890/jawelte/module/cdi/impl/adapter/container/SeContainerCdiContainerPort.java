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
package org.os890.jawelte.module.cdi.impl.adapter.container;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.api.port.CdiContainerPort;
import org.os890.jawelte.module.cdi.impl.adapter.extension.TestBeansCdiExtension;

/**
 * Default {@link CdiContainerPort} implementation. Wraps the Jakarta
 * CDI SE bootstrap API ({@link SeContainerInitializer}) with automatic
 * extension discovery left enabled, so the cdi-module's
 * {@link TestBeansCdiExtension} is contributed through CDI's standard
 * {@code ServiceLoader} mechanism (the
 * {@code META-INF/services/jakarta.enterprise.inject.spi.Extension}
 * file shipped in this module) — the same single registration path
 * every other jawelte module uses.
 *
 * <p>The extension is deliberately <em>not</em> also added via
 * {@code addExtensions(...)}: that would register it twice (once
 * programmatically, once via discovery), which some CDI SE
 * implementations de-duplicate and others do not, instantiating the
 * extension twice. Relying on discovery alone is also the only path
 * that works when the container is booted externally — e.g.
 * {@code @EnableTestBeans(manageContainer=false)}, where this port's
 * {@link #start(TestContext)} never runs and the user's own
 * {@code SeContainerInitializer.newInstance().initialize()} discovers
 * the extension from the service file.
 *
 * <p>Annotated {@code @Priority(Integer.MAX_VALUE)} so any
 * user-supplied implementation with a lower priority value
 * automatically wins via the project-wide
 * {@code ServicePriorityResolver}. A future quarkus-module's port
 * will ship at a lower priority and replace this impl seamlessly.
 *
 * <p>Loaded by {@link TestContext#loadService(Class)} from
 * {@code CdiTestBeanContainer.beforeAll(...)} / {@code afterAll(...)}.
 */
@Priority(Integer.MAX_VALUE)
public class SeContainerCdiContainerPort implements CdiContainerPort {

    /** No-arg constructor required by {@code ServiceLoader}. */
    public SeContainerCdiContainerPort() {
    }

    @Override
    public void start(TestContext testContext) {
        // Discovery is left enabled, so TestBeansCdiExtension is picked
        // up from META-INF/services exactly once — no addExtensions(...),
        // which would double-register it (see class javadoc).
        SeContainer container = SeContainerInitializer.newInstance().initialize();
        testContext.bindMetadata(SeContainer.class, container);
    }

    @Override
    public void stop(TestContext testContext) {
        testContext.getMetadata(SeContainer.class).ifPresent(container -> {
            try {
                container.close();
            } finally {
                testContext.unbindMetadata(SeContainer.class);
            }
        });
    }
}
