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
package org.os890.jawelte.module.cdi.impl;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;

import org.os890.jawelte.core.api.event.BeforeScopeStarted;
import org.os890.jawelte.core.api.event.ContainerStarted;
import org.os890.jawelte.core.api.port.TestBeanContainerPort;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.impl.extension.TestBeansCdiExtension;
import org.os890.jawelte.module.cdi.impl.util.InjectFieldsHelper;

/**
 * CDI SE adapter for {@link TestBeanContainerPort}. Boots a
 * {@link SeContainer} per test class, registers
 * {@link TestBeansCdiExtension}, fires {@link ContainerStarted}, and
 * manages the per-method {@link RequestContextController}.
 *
 * <p>Has <strong>no instance fields</strong> — per-test-class state
 * (the {@code SeContainer}, the {@code RequestContextController})
 * is bound on the {@link TestContext} via {@code bindMetadata}, so
 * the same provider instance is safe to reuse across test classes
 * and is unaffected by parallel test-class execution.
 *
 * <p>Discovered via {@code ServiceLoader} (the registration ships in
 * cdi-module/impl's
 * {@code META-INF/services/org.os890.jawelte.core.api.port.TestBeanContainerPort}).
 */
public class CdiTestBeanContainer implements TestBeanContainerPort {

    /** No-arg constructor required by {@code ServiceLoader}. */
    public CdiTestBeanContainer() {
    }

    @Override
    public void beforeAll(TestContext testContext) {
        SeContainerInitializer initializer = SeContainerInitializer.newInstance()
                .addExtensions(TestBeansCdiExtension.class);
        SeContainer container = initializer.initialize();
        testContext.bindMetadata(SeContainer.class, container);

        // Fire ContainerStarted while the container is still up,
        // before returning to DelegatingJUnitExtension. Module
        // lifecycle ports' beforeAll runs after this, so they are
        // guaranteed to observe ContainerStarted before their own
        // beforeAll is invoked.
        container.getBeanManager()
                .getEvent()
                .fire(new ContainerStarted(testContext.getTestClass()));
    }

    @Override
    public void postProcessTestInstance(TestContext testContext, Object testInstance) {
        SeContainer container = container(testContext);
        InjectFieldsHelper.inject(container.getBeanManager(), testInstance);
    }

    @Override
    public void beforeEach(TestContext testContext) {
        SeContainer container = container(testContext);
        BeforeScopeStarted event = new BeforeScopeStarted(RequestScoped.class);
        container.getBeanManager().getEvent().fire(event);
        if (event.isVetoed()) {
            return;
        }
        RequestContextController controller = container
                .select(RequestContextController.class, Any.Literal.INSTANCE)
                .get();
        controller.activate();
        testContext.bindMetadata(RequestContextController.class, controller);
    }

    @Override
    public void afterEach(TestContext testContext) {
        testContext.getMetadata(RequestContextController.class).ifPresent(controller -> {
            controller.deactivate();
            testContext.unbindMetadata(RequestContextController.class);
        });
    }

    @Override
    public void afterAll(TestContext testContext) {
        testContext.getMetadata(SeContainer.class).ifPresent(container -> {
            try {
                container.close();
            } finally {
                testContext.unbindMetadata(SeContainer.class);
            }
        });
    }

    private static SeContainer container(TestContext testContext) {
        return testContext.getMetadata(SeContainer.class)
                .orElseThrow(() -> new IllegalStateException(
                        "No SeContainer is bound on TestContext. Was beforeAll skipped via "
                                + "manageContainer=false without an externally booted container?"));
    }
}
