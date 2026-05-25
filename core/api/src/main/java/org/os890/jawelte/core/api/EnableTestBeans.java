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
package org.os890.jawelte.core.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.TestInstanceFactory;
import org.junit.jupiter.api.extension.TestInstanceFactoryContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.jupiter.api.extension.TestInstantiationException;
import org.os890.jawelte.core.api.port.TestBeansExtension;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestInstanceFactoryPort;

/**
 * Activates jawelte's CDI integration for a JUnit test class.
 *
 * <p>The annotation is meta-annotated with {@code @ExtendWith} pointing
 * at the nested {@link Proxy} class. JUnit instantiates the proxy via
 * its public no-arg constructor; on the first callback, the proxy uses
 * {@code ServiceLoader} to resolve the single {@link TestBeansExtension}
 * provider and delegates every JUnit lifecycle callback to it. Add
 * {@code jawelte-core-impl} to the test classpath to provide the
 * delegating extension implementation.
 *
 * <p>Zero or multiple {@link TestBeansExtension} providers cause the
 * proxy to throw an {@link IllegalStateException} on first use.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(EnableTestBeans.Proxy.class)
public @interface EnableTestBeans {

    /**
     * Whether to limit CDI bean discovery to {@code @TestBean}
     * declarations.
     *
     * <p>When {@code true}, all discovered beans except those declared
     * via {@code @TestBean} (on the test class or one of its
     * superclasses, directly or via meta-annotation) plus framework
     * internal types are vetoed. Auto-mocking is disabled. When
     * {@code false} (the default), normal CDI bean discovery applies
     * and unsatisfied injection points are auto-mocked.
     *
     * @return whether to limit bean discovery to {@code @TestBean}
     *         declarations; default {@code false}
     */
    boolean limitToTestBeans() default false;

    /**
     * Whether the framework should boot and shut down the CDI container
     * around the test class.
     *
     * <p>This flag has no effect on JUnit's own lifecycle &mdash; any
     * {@code @BeforeAll} / {@code @AfterAll} methods on the test class
     * are always called by JUnit regardless of the value.
     *
     * <p>When {@code true} (the default), the delegating extension calls
     * {@code TestBeanContainerPort.beforeAll(TestContext)} from inside
     * JUnit's {@code beforeAll} phase to boot the CDI container, and
     * {@code TestBeanContainerPort.afterAll(TestContext)} from inside
     * {@code afterAll} to shut it down.
     *
     * <p>When {@code false}, those two specific port invocations are
     * skipped; the user is responsible for booting the container (for
     * example via {@code SeContainerInitializer} from a
     * {@code @BeforeAll} method) and closing it from
     * {@code @AfterAll}. Everything else jawelte does still runs:
     * {@code TestModuleLifecyclePort.beforeAll} / {@code afterAll} on
     * every registered lifecycle port, and the per-method port methods
     * ({@code postProcessTestInstance}, {@code beforeEach},
     * {@code afterEach}) against the externally managed container.
     *
     * @return whether the framework manages the container lifecycle;
     *         default {@code true}
     */
    boolean manageContainer() default true;

    /**
     * The JUnit extension that bridges JUnit into jawelte's core. JUnit
     * instantiates this proxy via its no-arg constructor; on its first
     * callback, the proxy resolves the single
     * {@link TestBeansExtension} provider via {@code ServiceLoader} and
     * delegates every callback to it for the rest of the test class's
     * lifecycle.
     *
     * <p>The proxy lives in {@code core/api} so that {@code core/api}
     * remains the single user-visible entry point. The actual
     * implementation ({@code DelegatingJUnitExtension}) lives in
     * {@code core/impl}; {@code core/api} has no compile-time reference
     * to it.
     */
    class Proxy
            implements BeforeAllCallback,
                       BeforeEachCallback,
                       TestInstancePostProcessor,
                       AfterEachCallback,
                       AfterAllCallback,
                       TestInstanceFactory {

        private TestBeansExtension delegate;

        /**
         * No-arg constructor used by JUnit to instantiate the proxy
         * via {@code @ExtendWith(EnableTestBeans.Proxy.class)}. The
         * {@code public} modifier is implicit for members of a class
         * nested in an interface; declaring it explicitly here is
         * flagged by Checkstyle's RedundantModifier rule.
         */
        Proxy() {
        }

        @Override
        public void beforeAll(ExtensionContext extensionContext) throws Exception {
            delegate().beforeAll(extensionContext);
        }

        @Override
        public void beforeEach(ExtensionContext extensionContext) throws Exception {
            delegate().beforeEach(extensionContext);
        }

        @Override
        public void postProcessTestInstance(Object testInstance, ExtensionContext extensionContext) throws Exception {
            delegate().postProcessTestInstance(testInstance, extensionContext);
        }

        @Override
        public void afterEach(ExtensionContext extensionContext) throws Exception {
            delegate().afterEach(extensionContext);
        }

        @Override
        public void afterAll(ExtensionContext extensionContext) throws Exception {
            delegate().afterAll(extensionContext);
        }

        @Override
        public Object createTestInstance(
                TestInstanceFactoryContext factoryContext,
                ExtensionContext extensionContext) {
            Class<?> testClass = factoryContext.getTestClass();
            TestInstanceFactoryPort port = resolveTestInstancePort();
            try {
                if (port != null) {
                    Object portInstance = port.createInstance(testClass);
                    if (portInstance != null) {
                        return portInstance;
                    }
                }
                var constructor = testClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (TestInstantiationException tie) {
                throw tie;
            } catch (Exception e) {
                throw new TestInstantiationException(
                        "Could not create a test instance for " + testClass.getName(), e);
            } finally {
                // Close the bootstrap window for TestContext.get(): the
                // factory has produced the instance, so consumers inside
                // the test body must not see an active TestContext.
                // Pulled from the JUnit Store (populated by
                // DelegatingJUnitExtension.beforeAll) rather than via
                // TestContext.get() because the latter routes through MP
                // Config — and not every test classpath in the project
                // ships an MP Config implementation.
                Namespace namespace = Namespace.create(TestContext.class);
                TestContext storedContext = extensionContext.getStore(namespace)
                        .get(TestContext.class, TestContext.class);
                if (storedContext != null) {
                    storedContext.reset();
                }
            }
        }

        private static TestInstanceFactoryPort resolveTestInstancePort() {
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

        private synchronized TestBeansExtension delegate() {
            if (delegate == null) {
                delegate = resolveDelegate();
            }
            return delegate;
        }

        private static TestBeansExtension resolveDelegate() {
            List<TestBeansExtension> providers = new ArrayList<>();
            Iterator<TestBeansExtension> iterator =
                    ServiceLoader.load(TestBeansExtension.class).iterator();
            while (iterator.hasNext()) {
                providers.add(iterator.next());
            }

            if (providers.isEmpty()) {
                throw new IllegalStateException(
                        "No TestBeansExtension found via ServiceLoader. "
                                + "Add core-impl to the test classpath.");
            }
            if (providers.size() > 1) {
                throw new IllegalStateException(
                        "Multiple TestBeansExtension implementations found: ["
                                + providers.get(0).getClass().getName()
                                + ", "
                                + providers.get(1).getClass().getName()
                                + "]. Exactly one is required.");
            }
            return providers.get(0);
        }
    }
}
