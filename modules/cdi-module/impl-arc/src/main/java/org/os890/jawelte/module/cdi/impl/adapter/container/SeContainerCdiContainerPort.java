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
 * CDI SE bootstrap API ({@link SeContainerInitializer}) and registers
 * {@link TestBeansCdiExtension} so the cdi-module's bean-discovery
 * machinery is in place for every test class.
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
        SeContainerInitializer initializer = SeContainerInitializer.newInstance()
                .addExtensions(TestBeansCdiExtension.class);
        SeContainer container = initializer.initialize();
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
