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
import java.lang.reflect.Constructor;
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
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
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
     * <p>When {@code true} (the default), the delegating extension
     * calls {@code TestBeanContainerPort.beforeAll} and
     * {@code afterAll}. When {@code false}, those two calls are
     * skipped; the user is responsible for booting the container (for
     * example via {@code SeContainerInitializer}). The other lifecycle
     * methods ({@code postProcessTestInstance}, {@code beforeEach},
     * {@code afterEach}) still run against the externally managed
     * container.
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
                       InvocationInterceptor {

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
        public <T> T interceptTestClassConstructor(
                Invocation<T> invocation,
                ReflectiveInvocationContext<Constructor<T>> invocationContext,
                ExtensionContext extensionContext) throws Throwable {
            Class<?> testClass = invocationContext.getExecutable().getDeclaringClass();
            Namespace namespace = Namespace.create(TestContext.class);
            TestContext storedContext = extensionContext.getStore(namespace)
                    .get(TestContext.class, TestContext.class);
            try {
                // Priority-aware port lookup: a runtime integration
                // module (e.g. quarkus-module) ships a higher-@Priority
                // no-op port that returns null so this interceptor
                // calls invocation.proceed() — letting JUnit chain
                // through to the next InvocationInterceptor (under
                // {@code @QuarkusTest} that is Quarkus's own
                // {@code QuarkusTestExtension.interceptTestClassConstructor}
                // which produces the ArC-managed test instance).
                TestInstanceFactoryPort port = TestContext.loadService(TestInstanceFactoryPort.class);
                if (port != null) {
                    Object portInstance = port.createInstance(testClass);
                    if (portInstance != null) {
                        // Mark the invocation as handled — JUnit's
                        // interceptor chain requires either
                        // proceed() or skip(); returning a custom
                        // value alone trips
                        // "Chain of InvocationInterceptors never
                        // called invocation".
                        invocation.skip();
                        @SuppressWarnings("unchecked")
                        T cast = (T) portInstance;
                        return cast;
                    }
                }
                // Port absent or returned null: hand off to the next
                // interceptor in the chain. If no other interceptor
                // takes the call, JUnit invokes the constructor
                // reflectively for an unmanaged instance.
                return invocation.proceed();
            } finally {
                // Close the bootstrap window for TestContext.get(): the
                // interceptor chain has produced the instance, so
                // consumers inside the test body must not see an active
                // TestContext. Pulled from the JUnit Store (populated
                // by DelegatingJUnitExtension.beforeAll) rather than
                // via TestContext.get() because the latter routes
                // through MP Config — and not every test classpath in
                // the project ships an MP Config implementation.
                if (storedContext != null) {
                    storedContext.reset();
                }
            }
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
