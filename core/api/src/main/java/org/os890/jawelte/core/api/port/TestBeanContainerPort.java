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
package org.os890.jawelte.core.api.port;

/**
 * The single port implemented by a CDI runtime adapter. The delegating
 * JUnit extension forwards every test-lifecycle callback to this port.
 * Exactly one implementation must be on the classpath, discovered via
 * {@code ServiceLoader}.
 *
 * <p>This port has no JUnit API on its surface; the only shared type
 * is {@link TestContext}. Modules that genuinely need the JUnit
 * {@code ExtensionContext} retrieve it as metadata from {@link TestContext}
 * via {@code getMetadata(ExtensionContext.class)}. The two most common
 * lookups are short-cut as plain JDK types so module adapters can read
 * them without depending on the JUnit API:
 * {@code getMetadata(java.lang.reflect.Method.class)} returns the
 * current {@code @Test} method (set by the delegating extension's
 * {@code beforeEach}, refreshed across test methods); and
 * {@code getMetadata(Throwable.class)} returns the test body's
 * captured exception (refreshed in {@code afterEach}, absent on success).
 *
 * <p>Zero or multiple implementations cause the delegating extension
 * to throw an {@link IllegalStateException} on bootstrap.
 */
public interface TestBeanContainerPort {

    /**
     * Called once per test class during JUnit's {@code beforeAll}.
     * Implementations boot their CDI container.
     *
     * @param testContext the per-test-class context
     */
    void beforeAll(TestContext testContext);

    /**
     * Called once per test method during JUnit's {@code beforeEach},
     * after {@link #postProcessTestInstance(TestContext, Object)}.
     * Implementations activate a {@code RequestScoped} context (unless
     * vetoed via
     * {@link org.os890.jawelte.core.api.event.BeforeScopeStarted}).
     *
     * @param testContext the per-test-class context
     */
    void beforeEach(TestContext testContext);

    /**
     * Called once per test instance during JUnit's
     * {@code postProcessTestInstance}, after the test class constructor.
     * Implementations resolve {@code @Inject} fields on the test
     * instance against the container.
     *
     * @param testContext  the per-test-class context
     * @param testInstance the JUnit-created test instance
     */
    void postProcessTestInstance(TestContext testContext, Object testInstance);

    /**
     * Called once per test method during JUnit's {@code afterEach}.
     * Implementations deactivate the {@code RequestScoped} context if
     * it was activated during {@link #beforeEach(TestContext)}.
     *
     * @param testContext the per-test-class context
     */
    void afterEach(TestContext testContext);

    /**
     * Called once per test class during JUnit's {@code afterAll}.
     * Implementations shut down their CDI container if
     * {@code manageContainer=true} (the default for
     * {@code @EnableTestBeans}).
     *
     * @param testContext the per-test-class context
     */
    void afterAll(TestContext testContext);
}
