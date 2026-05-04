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

import java.util.Optional;

/**
 * Per-test-class facade exposed to {@link TestBeanContainerPort} and
 * {@link TestModuleLifecyclePort} implementations.
 *
 * <p>{@code TestContext} is the only argument every non-JUnit lifecycle
 * port method receives, so it is the boundary the rest of the framework
 * sees instead of the JUnit {@code ExtensionContext}. It is created by
 * the delegating JUnit extension during {@code beforeAll}, bound to the
 * class-level JUnit {@code ExtensionContext.Store} under the namespace
 * {@code TestContext.class}, and disposed by JUnit when the class-level
 * context closes.
 *
 * <p>The metadata API ({@link #bindMetadata(Class, Object)},
 * {@link #getMetadata(Class)}, {@link #unbindMetadata(Class)}) lets
 * modules stash per-test-class state. The {@code Class<T>} token is
 * both the key and the type witness; modules that need more than one
 * entry of the same type declare dedicated marker classes. Metadata
 * entries do not persist across test classes - they live with the
 * {@code TestContext} instance and die with the class-level Store.
 *
 * <p>The current JUnit {@code ExtensionContext} is also seeded as
 * metadata under the key {@code ExtensionContext.class} and refreshed
 * on every callback so the value is always the current JUnit context.
 * Modules that genuinely need a JUnit-specific capability retrieve it
 * via {@code getMetadata(ExtensionContext.class)}; this is the uniform
 * mechanism shared with every other metadata entry.
 */
public interface TestContext {

    /**
     * Get the test class currently being executed.
     *
     * @return the test class annotated with {@code @EnableTestBeans}
     */
    Class<?> getTestClass();

    /**
     * Bind a typed metadata entry to this {@code TestContext}.
     *
     * @param key   the type token used as both the key and type witness
     * @param value the value to bind under the key; must not be {@code null}
     * @param <T>   the type of the value
     */
    <T> void bindMetadata(Class<T> key, T value);

    /**
     * Look up a typed metadata entry on this {@code TestContext}.
     *
     * @param key the type token previously used to bind a value
     * @param <T> the type of the value
     * @return the value, or {@link Optional#empty()} if no value is bound
     */
    <T> Optional<T> getMetadata(Class<T> key);

    /**
     * Remove a typed metadata entry from this {@code TestContext}.
     *
     * @param key the type token previously used to bind a value
     * @param <T> the type of the value
     */
    <T> void unbindMetadata(Class<T> key);
}
