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
package org.os890.jawelte.core.impl.adapter.context;

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
 * {@link #reset()} method. It is called primarily by
 * {@code EnableTestBeans.Proxy} (the JUnit {@code TestInstanceFactory})
 * once the test instance has been created, and again as a safety net by
 * {@code DelegatingJUnitExtension.afterAll} (covering test classes that
 * are not created through the factory bridge — e.g. {@code @QuarkusTest}
 * or a class without {@code @EnableTestBeans}). {@code reset()} is
 * idempotent and best-effort same-thread: it clears the slot only when
 * the calling thread's {@code CURRENT} still holds this instance.
 *
 * <p><strong>Threading model (same-thread assumption).</strong>
 * {@code CURRENT} is set in {@link #TestContextImpl(Class)} on the
 * {@code beforeAll} thread, read by every CDI extension via
 * {@link TestContext#get()} during the container bootstrap that
 * {@code beforeAll} drives <em>synchronously</em> (so on that same
 * thread), and cleared by {@link #reset()} on the
 * {@code TestInstanceFactory} / {@code afterAll} thread. Under the
 * framework's supported execution model — single-threaded, one test
 * class per JVM, no JUnit parallel execution ({@code -T 1}, no parallel
 * Surefire config) — all of these run on the <em>same</em> thread, so
 * the set-here / clear-there split is exact and the slot is always
 * cleared.
 *
 * <p>If a consumer were to enable JUnit parallel execution and JUnit
 * ran {@code beforeAll} and the factory/{@code afterAll} on different
 * threads, the cross-thread {@link #reset()} would no-op (a
 * {@link ThreadLocal} can only clear the calling thread's slot), leaving
 * the {@code beforeAll} thread's slot populated. That is <em>not</em> a
 * correctness hazard for {@link TestContext#get()}: the constructor's
 * {@code CURRENT.set(this)} overwrites any stale slot, and JUnit always
 * runs a test class's {@code beforeAll} (hence the constructor) before
 * any {@code get()} on that thread — so {@code get()} never returns a
 * leaked context. The only residue is the prior instance being retained
 * by the pooled thread until it is reused (overwritten) or dies.
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
     * the same thread, until {@link #reset()} clears the slot. The
     * {@code CURRENT.set(this)} call <em>overwrites</em> any slot left
     * uncleared by a prior test on this thread, which is the
     * self-healing backstop should a cross-thread {@link #reset()} ever
     * have no-opped (see the class-level threading note).
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
