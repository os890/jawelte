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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.platform.commons.support.AnnotationSupport;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.port.TestBeanContainerPort;
import org.os890.jawelte.core.api.port.TestBeansExtension;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;
import org.os890.jawelte.core.impl.adapter.context.TestContextImpl;
import org.os890.jawelte.core.impl.loader.ServiceLoaderCache;

/**
 * Single {@link TestBeansExtension} provider on the classpath; loaded
 * by {@code EnableTestBeans.Proxy} via {@code ServiceLoader}.
 *
 * <p>Per TICKET-001:
 * <ul>
 *   <li>Creates a {@link TestContext} in {@code beforeAll}, binds the
 *       current JUnit {@code ExtensionContext} as metadata, and stores
 *       the context in the JUnit {@code ExtensionContext.Store} under
 *       the namespace {@code TestContext.class}. JUnit disposes the
 *       Store entry when the class-level context closes; no explicit
 *       cleanup of {@code TestContext} state is performed here.</li>
 *   <li>Resolves the single {@link TestBeanContainerPort} and zero or
 *       more {@link TestModuleLifecyclePort} implementations via
 *       {@link ServiceLoaderCache} (priority-sorted). Both are cached
 *       at classloader scope; not re-resolved per test class or per
 *       test method.</li>
 *   <li>Honors {@code @EnableTestBeans.manageContainer}: when
 *       {@code true} (the default), invokes
 *       {@code TestBeanContainerPort.beforeAll}/{@code afterAll};
 *       when {@code false}, those calls are skipped while the rest of
 *       the lifecycle still runs.</li>
 *   <li>Invokes {@code before*} callbacks in ascending {@code @Priority}
 *       order and {@code after*} callbacks in reverse (LIFO) order;
 *       only ports that completed their corresponding {@code before*}
 *       receive an {@code after*} call (cleanup guarantee).</li>
 *   <li>Aggregates exceptions during {@code afterEach} / {@code afterAll}
 *       per the contracts in TICKET-001 ("Exception Aggregation Policy"
 *       and "Unconditional Cleanup Guarantees"): the first thrown
 *       exception is the primary; the rest are attached via
 *       {@link Throwable#addSuppressed(Throwable)}.
 *       {@link TestBeanContainerPort#afterEach(TestContext)} and
 *       {@link TestBeanContainerPort#afterAll(TestContext)} run
 *       unconditionally even when every module port threw.</li>
 * </ul>
 */
public class DelegatingJUnitExtension implements TestBeansExtension {

    private static final Namespace NAMESPACE = Namespace.create(TestContext.class);
    private static final String COMPLETED_BEFORE_ALL_KEY = "completedBeforeAll";
    private static final String COMPLETED_BEFORE_EACH_KEY = "completedBeforeEach";
    private static final String MANAGE_CONTAINER_KEY = "manageContainer";

    private static final Set<String> QUARKUS_TEST_ANNOTATIONS = Set.of(
            "io.quarkus.test.junit.QuarkusTest",
            "io.quarkus.test.junit.QuarkusComponentTest");

    /**
     * No-arg constructor used by {@code ServiceLoader}.
     */
    public DelegatingJUnitExtension() {
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        Class<?> testClass = extensionContext.getRequiredTestClass();
        boolean manageContainer = readManageContainer(testClass);

        TestContext testContext = new TestContextImpl(testClass);
        testContext.bindMetadata(ExtensionContext.class, extensionContext);

        Store store = store(extensionContext);
        store.put(TestContext.class, testContext);
        store.put(MANAGE_CONTAINER_KEY, manageContainer);
        List<TestModuleLifecyclePort> completed = new ArrayList<>();
        store.put(COMPLETED_BEFORE_ALL_KEY, completed);

        TestBeanContainerPort containerPort = ServiceLoaderCache.resolveContainerPort();
        List<TestModuleLifecyclePort> lifecyclePorts = ServiceLoaderCache.resolveLifecyclePorts();

        if (manageContainer) {
            containerPort.beforeAll(testContext);
        }
        for (TestModuleLifecyclePort port : lifecyclePorts) {
            port.beforeAll(testContext);
            completed.add(port);
        }
        // TICKET-016: the TestContext ThreadLocal now lives until
        // EnableTestBeans.Proxy (the JUnit TestInstanceFactory) has
        // finished creating the JUnit test instance (which may go
        // through CDI and therefore needs the active TestContext to be
        // visible to TestBeansCdiExtension). The factory calls reset()
        // once the instance is in hand. If the factory isn't invoked
        // (e.g. the test class isn't created through the JUnit
        // auto-detected factory bridge), afterAll resets as a safety net.
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext extensionContext) throws Exception {
        TestContext testContext = retrieveAndRefresh(extensionContext);
        TestBeanContainerPort containerPort = ServiceLoaderCache.resolveContainerPort();
        containerPort.postProcessTestInstance(testContext, testInstance);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        TestContext testContext = retrieveAndRefresh(extensionContext);
        bindTestMethodMetadata(testContext, extensionContext);
        bindExecutionExceptionMetadata(testContext, extensionContext);
        Store store = store(extensionContext);
        List<TestModuleLifecyclePort> completed = new ArrayList<>();
        store.put(COMPLETED_BEFORE_EACH_KEY, completed);

        TestBeanContainerPort containerPort = ServiceLoaderCache.resolveContainerPort();
        List<TestModuleLifecyclePort> lifecyclePorts = ServiceLoaderCache.resolveLifecyclePorts();

        containerPort.beforeEach(testContext);

        for (TestModuleLifecyclePort port : lifecyclePorts) {
            port.beforeEach(testContext);
            completed.add(port);
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        TestContext testContext = retrieveAndRefresh(extensionContext);
        // Refresh execution-exception metadata: getExecutionException() is empty
        // during beforeEach and populated after the test body returns. Module
        // adapters reading testContext.getMetadata(Throwable.class) in their
        // afterEach hook see the actual outcome.
        bindExecutionExceptionMetadata(testContext, extensionContext);
        List<TestModuleLifecyclePort> completed = lookupCompleted(extensionContext, COMPLETED_BEFORE_EACH_KEY);

        List<Throwable> collected = new ArrayList<>();
        for (int i = completed.size() - 1; i >= 0; i--) {
            try {
                completed.get(i).afterEach(testContext);
            } catch (Throwable t) {
                collected.add(t);
            }
        }
        try {
            ServiceLoaderCache.resolveContainerPort().afterEach(testContext);
        } catch (Throwable t) {
            collected.add(t);
        }

        rethrowAggregated(collected);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        TestContext testContext = retrieveAndRefresh(extensionContext);
        boolean manageContainer = store(extensionContext)
                .getOrDefault(MANAGE_CONTAINER_KEY, Boolean.class, Boolean.TRUE);
        List<TestModuleLifecyclePort> completed = lookupCompleted(extensionContext, COMPLETED_BEFORE_ALL_KEY);

        List<Throwable> collected = new ArrayList<>();
        for (int i = completed.size() - 1; i >= 0; i--) {
            try {
                completed.get(i).afterAll(testContext);
            } catch (Throwable t) {
                collected.add(t);
            }
        }
        if (manageContainer) {
            try {
                ServiceLoaderCache.resolveContainerPort().afterAll(testContext);
            } catch (Throwable t) {
                collected.add(t);
            }
        }

        // TICKET-016 safety net: clear the TestContext ThreadLocal in
        // case EnableTestBeans.Proxy (the TestInstanceFactory) didn't
        // (the factory might be skipped under @QuarkusTest or for any
        // test class that doesn't carry @EnableTestBeans). The
        // instance's reset() is idempotent — does nothing if the slot
        // is already empty.
        testContext.reset();

        rethrowAggregated(collected);
    }

    private static TestContext retrieveAndRefresh(ExtensionContext extensionContext) {
        TestContext testContext = store(extensionContext).get(TestContext.class, TestContext.class);
        testContext.bindMetadata(ExtensionContext.class, extensionContext);
        return testContext;
    }

    /**
     * Bind the current {@code @Test} method as
     * {@code TestContext.getMetadata(Method.class)} so module adapters
     * read it directly without reflecting on the JUnit
     * {@link ExtensionContext}. Unbinds when the
     * {@link ExtensionContext} reports no test method (e.g. between
     * test classes).
     */
    private static void bindTestMethodMetadata(TestContext testContext, ExtensionContext extensionContext) {
        extensionContext.getTestMethod().ifPresentOrElse(
                method -> testContext.bindMetadata(Method.class, method),
                () -> testContext.unbindMetadata(Method.class));
    }

    /**
     * Bind the current execution exception as
     * {@code TestContext.getMetadata(Throwable.class)}. {@code beforeEach}
     * always clears (test body hasn't run yet); {@code afterEach}
     * populates with the captured {@link Throwable} when the test
     * threw, or leaves it cleared on success.
     */
    private static void bindExecutionExceptionMetadata(
            TestContext testContext, ExtensionContext extensionContext) {
        extensionContext.getExecutionException().ifPresentOrElse(
                throwable -> testContext.bindMetadata(Throwable.class, throwable),
                () -> testContext.unbindMetadata(Throwable.class));
    }

    @SuppressWarnings("unchecked")
    private static List<TestModuleLifecyclePort> lookupCompleted(ExtensionContext extensionContext, String key) {
        List<?> raw = store(extensionContext).getOrDefault(key, List.class, List.of());
        return (List<TestModuleLifecyclePort>) raw;
    }

    private static Store store(ExtensionContext extensionContext) {
        return extensionContext.getStore(NAMESPACE);
    }

    /**
     * Resolves the effective {@code manageContainer} flag for the
     * given test class. Returns {@code false} when:
     * <ul>
     *   <li>{@code @EnableTestBeans(manageContainer=false)} is set, or</li>
     *   <li>the test class carries
     *       {@code io.quarkus.test.junit.QuarkusTest} or
     *       {@code io.quarkus.test.junit.QuarkusComponentTest} (compared
     *       by FQN string so core/impl incurs no compile-time dependency
     *       on Quarkus). The Quarkus test framework already manages a
     *       bean container for the test class, so jawelte must not
     *       boot a second one.</li>
     * </ul>
     *
     * @param testClass the JUnit test class
     * @return {@code true} if jawelte should boot/shut down the
     *         container; {@code false} otherwise
     */
    private static boolean readManageContainer(Class<?> testClass) {
        if (hasQuarkusTestAnnotation(testClass)) {
            return false;
        }
        Optional<EnableTestBeans> config =
                AnnotationSupport.findAnnotation(testClass, EnableTestBeans.class);
        return config.map(EnableTestBeans::manageContainer).orElse(true);
    }

    private static boolean hasQuarkusTestAnnotation(Class<?> testClass) {
        for (Annotation annotation : testClass.getAnnotations()) {
            if (QUARKUS_TEST_ANNOTATIONS.contains(annotation.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }

    private static void rethrowAggregated(List<Throwable> collected) throws Exception {
        if (collected.isEmpty()) {
            return;
        }
        Throwable primary = collected.get(0);
        for (int i = 1; i < collected.size(); i++) {
            Throwable next = collected.get(i);
            if (next != primary) {
                primary.addSuppressed(next);
            }
        }
        if (primary instanceof Exception ex) {
            throw ex;
        }
        if (primary instanceof Error err) {
            throw err;
        }
        throw new RuntimeException(primary);
    }
}
