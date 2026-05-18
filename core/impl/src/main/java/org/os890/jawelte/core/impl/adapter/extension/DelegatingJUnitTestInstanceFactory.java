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
package org.os890.jawelte.core.impl.adapter.extension;

import java.util.Iterator;
import java.util.ServiceLoader;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstanceFactory;
import org.junit.jupiter.api.extension.TestInstanceFactoryContext;
import org.junit.jupiter.api.extension.TestInstantiationException;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestInstanceFactoryPort;

/**
 * Bridges JUnit's {@link TestInstanceFactory} SPI to jawelte's
 * {@link TestInstanceFactoryPort}. Registered via JUnit's
 * auto-detection mechanism — the activation file lives in
 * {@code cdi-module/impl}'s
 * {@code META-INF/services/org.junit.jupiter.api.extension.Extension},
 * so the factory is only on the classpath when cdi-module/impl is
 * (i.e. under {@code -Powb} / {@code -Pweld}, not under
 * {@code -Pquarkus} where Quarkus's own {@code TestInstanceFactory}
 * owns instance creation).
 *
 * <p>For every test class JUnit asks for an instance, the bridge:
 * <ul>
 *   <li>Resolves a single {@link TestInstanceFactoryPort} via
 *       {@link ServiceLoader}.</li>
 *   <li>Delegates the lookup to the port and returns its result.</li>
 *   <li>Falls back to reflection
 *       ({@code testClass.getDeclaredConstructor().newInstance()})
 *       when no port implementation is present, so tests outside the
 *       {@code @EnableTestBeans} surface keep the JUnit-default
 *       behaviour.</li>
 * </ul>
 *
 * <p>The port is resolved per-call (no caching) — JUnit caches the
 * factory itself, so this method is invoked once per test instance,
 * not per test method.
 */
public class DelegatingJUnitTestInstanceFactory implements TestInstanceFactory {

    /** No-arg constructor used by JUnit's auto-detection. */
    public DelegatingJUnitTestInstanceFactory() {
    }

    @Override
    public Object createTestInstance(
            TestInstanceFactoryContext factoryContext,
            ExtensionContext extensionContext) {
        Class<?> testClass = factoryContext.getTestClass();
        TestInstanceFactoryPort port = loadPort();
        Object instance;
        try {
            if (port != null) {
                Object portInstance = port.createInstance(testClass);
                instance = portInstance != null ? portInstance : reflectiveInstance(testClass);
            } else {
                instance = reflectiveInstance(testClass);
            }
        } catch (TestInstantiationException tie) {
            throw tie;
        } catch (Exception e) {
            throw new TestInstantiationException(
                    "Could not create a test instance for " + testClass.getName(), e);
        }
        // TICKET-016 bootstrap-window close. DelegatingJUnitExtension.beforeAll
        // no longer resets the TestContext, so the user's @BeforeAll (or any
        // CDI-extension-driven discovery triggered during it) can still see
        // an active context. Once the test instance is in hand, the window
        // is closed: TestContext.get() throws inside the test body, matching
        // the long-standing "no TestContext outside bootstrap" invariant.
        try {
            TestContext.get().reset();
        } catch (IllegalStateException noActiveContext) {
            // Test class didn't go through jawelte's beforeAll (e.g. a
            // standalone JUnit test without @EnableTestBeans). Nothing to
            // reset.
        }
        return instance;
    }

    private static Object reflectiveInstance(Class<?> testClass) throws Exception {
        return testClass.getDeclaredConstructor().newInstance();
    }

    private static TestInstanceFactoryPort loadPort() {
        Iterator<TestInstanceFactoryPort> iterator =
                ServiceLoader.load(TestInstanceFactoryPort.class).iterator();
        if (!iterator.hasNext()) {
            return null;
        }
        TestInstanceFactoryPort port = iterator.next();
        if (iterator.hasNext()) {
            throw new IllegalStateException(
                    "Multiple TestInstanceFactoryPort implementations found; "
                            + "the SPI is single-impl. First: " + port.getClass().getName()
                            + ", next: " + iterator.next().getClass().getName());
        }
        return port;
    }
}
