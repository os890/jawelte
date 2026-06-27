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

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;

import org.os890.jawelte.core.api.event.BeforeScopeStarted;
import org.os890.jawelte.core.api.event.ContainerStarted;
import org.os890.jawelte.core.api.port.TestBeanContainerPort;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.api.port.CdiContainerPort;

/**
 * Adapter for {@link TestBeanContainerPort} that delegates the bean
 * container lifecycle to the active {@link CdiContainerPort}. The
 * default {@code CdiContainerPort} (in {@code cdi-module/impl}) is
 * SE-based; a future quarkus-module ships its own
 * {@code CdiContainerPort} implementation that wins via a lower
 * {@code @Priority} value, and {@code CdiTestBeanContainer} continues
 * to coordinate the rest of the per-test-class lifecycle (firing
 * {@link ContainerStarted}, activating / deactivating the request
 * scope, populating {@code @Inject} fields on the JUnit-provided test
 * instance) regardless of which CDI flavour is in play.
 *
 * <p>Has <strong>no instance fields</strong> — per-test-class state
 * (the container handle, the {@code RequestContextController}) is
 * bound on the {@link TestContext} via {@code bindMetadata}, so the
 * same provider instance is safe to reuse across test classes and is
 * unaffected by parallel test-class execution.
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
        CdiContainerPort containerPort = TestContext.loadService(CdiContainerPort.class);
        containerPort.start(testContext);

        // Fire ContainerStarted while the container is up, before
        // returning to DelegatingJUnitExtension. Module lifecycle
        // ports' beforeAll runs after this, so they are guaranteed
        // to observe ContainerStarted before their own beforeAll is
        // invoked. Uses CDI.current() so the firing path stays
        // container-flavour-agnostic.
        CDI.current()
                .getBeanManager()
                .getEvent()
                .fire(new ContainerStarted(testContext.getTestClass()));
    }

    @Override
    public void postProcessTestInstance(TestContext testContext, Object testInstance) {
        // No-op. The test instance comes from EnableTestBeans.Proxy
        // (the TestInstanceFactory) via CdiTestInstanceFactoryPortAdapter,
        // which routes through
        // CDI.current().select(testClass).get() so CDI's normal
        // bean-instantiation path populates the @Inject fields. Any
        // scenario where the test instance is NOT a CDI bean (e.g.
        // a future @EnableTestBeans(manageContainer=false) variant
        // where jawelte's extension never sees the user's container)
        // will have to handle its own injection — the framework no
        // longer falls back to manual InjectionTarget population.
    }

    @Override
    public void beforeEach(TestContext testContext) {
        BeanManager beanManager = beanManager(testContext);
        BeforeScopeStarted event = new BeforeScopeStarted(RequestScoped.class);
        beanManager.getEvent().fire(event);
        if (event.isVetoed()) {
            return;
        }
        RequestContextController controller = beanManager
                .createInstance()
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
        TestContext.loadService(CdiContainerPort.class).stop(testContext);
    }

    private static BeanManager beanManager(TestContext testContext) {
        return testContext.getMetadata(SeContainer.class)
                .map(SeContainer::getBeanManager)
                .orElseGet(() -> CDI.current().getBeanManager());
    }
}
