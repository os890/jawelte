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
 * The port implemented by feature modules that need to participate in
 * the test lifecycle (e.g. test-scope activation, JPA transaction
 * management). Multiple implementations are allowed; the delegating
 * extension sorts them by {@code jakarta.annotation.Priority} and
 * invokes them in ascending priority order for {@code before*}
 * callbacks and in reverse (LIFO) order for {@code after*} callbacks.
 * Implementations without {@code @Priority} get an effective priority
 * of {@code Integer.MAX_VALUE} (called last in {@code before*}; first
 * in {@code after*}).
 *
 * <p>Like {@link TestBeanContainerPort}, this interface has no JUnit
 * API on its surface; the only shared type is {@link TestContext}.
 *
 * <p>All methods default to no-op so an implementation only overrides
 * the callbacks it actually cares about.
 */
public interface TestModuleLifecyclePort {

    /**
     * Called once per test class during JUnit's {@code beforeAll},
     * after {@link TestBeanContainerPort#beforeAll(TestContext)}.
     *
     * @param testContext the per-test-class context
     */
    default void beforeAll(TestContext testContext) {
    }

    /**
     * Called once per test method during JUnit's {@code beforeEach},
     * after {@link TestBeanContainerPort#beforeEach(TestContext)}.
     *
     * @param testContext the per-test-class context
     */
    default void beforeEach(TestContext testContext) {
    }

    /**
     * Called once per test method during JUnit's {@code afterEach},
     * before {@link TestBeanContainerPort#afterEach(TestContext)}.
     *
     * @param testContext the per-test-class context
     */
    default void afterEach(TestContext testContext) {
    }

    /**
     * Called once per test class during JUnit's {@code afterAll},
     * before {@link TestBeanContainerPort#afterAll(TestContext)}.
     *
     * @param testContext the per-test-class context
     */
    default void afterAll(TestContext testContext) {
    }
}
