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
package org.os890.jawelte.core.impl.context;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.os890.jawelte.core.api.port.TestContext;

/**
 * Default {@link TestContext} implementation. Plays two roles:
 *
 * <ol>
 *   <li><strong>Per-test instance</strong> (constructed via
 *       {@link #TestContextImpl(Class)} by
 *       {@code core/impl}'s {@code DelegatingJUnitExtension.beforeAll}):
 *       holds the per-test data ({@code testClass} + the metadata map)
 *       and self-registers on the class-level static {@link ThreadLocal}
 *       so {@link TestContext#get()} returns this instance from any
 *       caller on the same thread.</li>
 *   <li><strong>Accessor instance</strong> (constructed via
 *       {@link #TestContextImpl()} by {@link TestContext#get()}'s
 *       reflective bootstrap path): only services the SPI methods
 *       {@link #getCurrent()} and {@link #reset()}; calling any
 *       per-test method throws {@link IllegalStateException}.</li>
 * </ol>
 *
 * <p>Cleanup of the per-thread registration is the framework-internal
 * {@link #reset()} method, called by {@code DelegatingJUnitExtension}
 * on its own local per-test reference in the {@code beforeAll}
 * {@code finally} block.
 */
public class TestContextImpl implements TestContext {

    private static final ThreadLocal<TestContextImpl> CURRENT = new ThreadLocal<>();

    private final Class<?> testClass;
    private final Map<Class<?>, Object> metadata;

    /**
     * Accessor constructor used by {@link TestContext#get()}'s
     * reflective bootstrap path. The resulting instance only services
     * {@link #getCurrent()} / {@link #reset()}; all per-test methods
     * throw.
     */
    public TestContextImpl() {
        this.testClass = null;
        this.metadata = null;
    }

    /**
     * Per-test constructor used by
     * {@code DelegatingJUnitExtension.beforeAll}. Self-registers on
     * the class-level static {@link ThreadLocal} so that
     * {@link TestContext#get()} returns this instance to any caller on
     * the same thread, until {@link #reset()} clears the slot.
     *
     * @param testClass the test class annotated with
     *                  {@code @EnableTestBeans}; must not be {@code null}
     */
    public TestContextImpl(Class<?> testClass) {
        this.testClass = Objects.requireNonNull(testClass, "testClass");
        this.metadata = new HashMap<>();
        CURRENT.set(this);
    }

    @Override
    public Class<?> getTestClass() {
        if (testClass == null) {
            throw accessorOnly("getTestClass()");
        }
        return testClass;
    }

    @Override
    public <T> void bindMetadata(Class<T> key, T value) {
        if (metadata == null) {
            throw accessorOnly("bindMetadata(...)");
        }
        Objects.requireNonNull(key, "metadata key");
        Objects.requireNonNull(value, "metadata value");
        metadata.put(key, value);
    }

    @Override
    public <T> Optional<T> getMetadata(Class<T> key) {
        if (metadata == null) {
            throw accessorOnly("getMetadata(...)");
        }
        if (key == null) {
            return Optional.empty();
        }
        Object value = metadata.get(key);
        return Optional.ofNullable(key.cast(value));
    }

    @Override
    public <T> void unbindMetadata(Class<T> key) {
        if (metadata == null) {
            throw accessorOnly("unbindMetadata(...)");
        }
        if (key == null) {
            return;
        }
        metadata.remove(key);
    }

    @Override
    public TestContext getCurrent() {
        return CURRENT.get();
    }

    @Override
    public void reset() {
        if (testClass != null && CURRENT.get() == this) {
            CURRENT.remove();
        }
    }

    private static IllegalStateException accessorOnly(String methodName) {
        return new IllegalStateException(
                methodName + " was called on an accessor TestContext instance. "
                        + "This instance was constructed via the no-arg constructor for use by "
                        + "TestContext.get() and only supports getCurrent() / reset(). "
                        + "Per-test methods require an instance constructed via "
                        + "TestContextImpl(Class<?>) by DelegatingJUnitExtension.");
    }
}
