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

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

import org.os890.jawelte.core.api.port.TestInstanceFactoryPort;

/**
 * cdi-module's implementation of {@link TestInstanceFactoryPort}.
 * Resolves the test instance through {@link CDI#current()} — the test
 * class itself is treated as a {@code @Dependent} bean (configured by
 * {@code TestBeansCdiExtension}) so the lookup returns the real test
 * object, not a CDI proxy. Field injection happens during CDI's
 * normal instantiation path; JUnit receives the fully-populated bean.
 *
 * <p>Loaded by core's {@code EnableTestBeans.Proxy} (the JUnit
 * {@code TestInstanceFactory}) via {@code ServiceLoader}; the
 * registration ships in this module's
 * {@code META-INF/services/org.os890.jawelte.core.api.port.TestInstanceFactoryPort}.
 *
 * <p>The lookup runs after
 * {@link org.os890.jawelte.core.api.port.TestBeanContainerPort#beforeAll(org.os890.jawelte.core.api.port.TestContext)}
 * has bootstrapped the container (JUnit's default
 * {@code PER_METHOD} lifecycle invokes {@code TestInstanceFactory}
 * after {@code @BeforeAll}); under the alternative
 * {@code PER_CLASS} lifecycle the container isn't booted yet when the
 * factory runs, which is a known limitation documented in
 * TICKET-016's open questions.
 */
public class CdiTestInstanceFactoryPortAdapter implements TestInstanceFactoryPort {

    /** No-arg constructor required by {@code ServiceLoader}. */
    public CdiTestInstanceFactoryPortAdapter() {
    }

    @Override
    public Object createInstance(Class<?> testClass) {
        try {
            Instance<?> instance = CDI.current().select(testClass);
            if (instance.isUnsatisfied()) {
                // Container is active but the test class isn't a CDI
                // bean (e.g. manage-container-false where the
                // user-managed container never saw
                // TestBeansCdiExtension register the test class as
                // @Dependent). Bridge falls back to reflection.
                return null;
            }
            return instance.get();
        } catch (IllegalStateException noActiveContainer) {
            // CDI's `select` (OpenWebBeans) and `current` (Weld)
            // both throw when no container is active. Most likely
            // the test class doesn't use @EnableTestBeans, or it
            // does but with manageContainer=false and hasn't yet
            // booted its own container. Bridge falls back to
            // reflection in either case.
            return null;
        }
    }
}
