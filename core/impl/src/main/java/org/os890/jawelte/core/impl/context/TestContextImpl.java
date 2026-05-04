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
 * Default {@link TestContext} implementation. Created by
 * {@code DelegatingJUnitExtension} during {@code beforeAll} and bound
 * to the JUnit class-level {@code ExtensionContext.Store} under the
 * namespace {@code TestContext.class}; disposed automatically by JUnit
 * when the class-level context closes.
 *
 * <p>The metadata API is backed by an in-memory {@link HashMap}. The
 * {@code Class<T>} token serves as both key and type witness; the cast
 * inside {@link #getMetadata(Class)} is safe by construction because
 * {@link #bindMetadata(Class, Object)} stores values typed by the same
 * key.
 */
public class TestContextImpl implements TestContext {

    private final Class<?> testClass;
    private final Map<Class<?>, Object> metadata = new HashMap<>();

    /**
     * Construct a {@code TestContextImpl} for the given test class.
     *
     * @param testClass the test class annotated with
     *                  {@code @EnableTestBeans}
     */
    public TestContextImpl(Class<?> testClass) {
        this.testClass = Objects.requireNonNull(testClass, "testClass");
    }

    @Override
    public Class<?> getTestClass() {
        return testClass;
    }

    @Override
    public <T> void bindMetadata(Class<T> key, T value) {
        Objects.requireNonNull(key, "metadata key");
        Objects.requireNonNull(value, "metadata value");
        metadata.put(key, value);
    }

    @Override
    public <T> Optional<T> getMetadata(Class<T> key) {
        if (key == null) {
            return Optional.empty();
        }
        Object value = metadata.get(key);
        return Optional.ofNullable(key.cast(value));
    }

    @Override
    public <T> void unbindMetadata(Class<T> key) {
        if (key == null) {
            return;
        }
        metadata.remove(key);
    }
}
