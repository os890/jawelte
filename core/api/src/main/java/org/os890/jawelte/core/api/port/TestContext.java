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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import jakarta.enterprise.inject.spi.CDI;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

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
 *
 * <h2>Static accessors</h2>
 *
 * <p>{@link #get()} returns the {@code TestContext} active on the
 * current thread. It resolves the accessor implementation class via
 * MicroProfile Config (key = this interface's own full class name;
 * dot-then-underscore fallback applies), instantiates it reflectively
 * via its public no-arg constructor, and delegates to
 * {@link #getCurrent()} on that accessor. Used by CDI extensions and
 * other bootstrap-time consumers that need the active context but
 * have no parameter to receive it through.
 *
 * <p>{@link #loadService(Class)} is the project-wide canonical entry
 * point for prioritized SPI lookup. Pass the port interface and get
 * back the active implementation. The static method body encapsulates
 * the priority sort and the bootstrap of {@link ServicePriorityResolver}
 * itself; callers do not look up the resolver themselves and do not
 * enumerate candidates themselves.
 *
 * <p>The two SPI methods {@link #getCurrent()} and {@link #reset()} are
 * framework-internal — they back {@link #get()} and {@code core/impl}'s
 * cleanup path respectively. User code calls the static {@link #get()}
 * helper instead of {@code getCurrent()} directly.
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

    /**
     * Framework-internal SPI. Returns the {@code TestContext} active
     * on the calling thread, or {@code null} if none.
     *
     * <p>Implementations back this with a class-level
     * {@code static ThreadLocal<TestContext>}. User code calls the
     * static {@link #get()} helper instead.
     *
     * @return the per-thread active {@code TestContext}, or {@code null}
     */
    TestContext getCurrent();

    /**
     * Framework-internal SPI. Clears the active {@code TestContext}
     * on the calling thread.
     *
     * <p>Called by {@code core/impl} on its own local {@code TestContext}
     * reference in the {@code beforeAll} {@code finally} block, after
     * {@link TestBeanContainerPort#beforeAll(TestContext)} returns or
     * throws.
     */
    void reset();

    /**
     * Returns the {@code TestContext} active on the current thread.
     *
     * <p>Resolves the accessor implementation class via MicroProfile
     * Config (key = the {@code TestContext} interface's own full class
     * name; dot-then-underscore fallback applies, value = the FQCN of
     * a class implementing {@code TestContext} with a public no-arg
     * constructor), instantiates the accessor reflectively, and
     * delegates to {@link #getCurrent()} on the result. The lookup is
     * uncached — every call repeats the MP Config / reflective
     * instantiation, since the accessor itself is essentially stateless
     * and {@code get()} is invoked only on the brief CDI bootstrap
     * window.
     *
     * @return the per-thread active {@code TestContext}
     * @throws IllegalStateException if no {@code TestContext} is active
     *         on the current thread or if the configured accessor
     *         cannot be loaded
     */
    static TestContext get() {
        TestContext accessor = instantiateConfigured(TestContext.class, false);
        TestContext current = accessor.getCurrent();
        if (current == null) {
            throw new IllegalStateException(
                    "No TestContext is active on the current thread. "
                            + "TestContext.get() may only be called inside the bootstrap window of "
                            + "core/impl's DelegatingJUnitExtension.beforeAll.");
        }
        return current;
    }

    /**
     * Returns the active implementation of the given SPI port type —
     * the project-wide canonical mechanism for prioritized SPI
     * lookup.
     *
     * <p>Two cases:
     * <ul>
     *   <li><strong>{@code targetType == ServicePriorityResolver.class}</strong>:
     *       reads the MP Config key whose name is
     *       {@code ServicePriorityResolver}'s own FQCN, resolves the
     *       configured class, and returns an instance — first via
     *       {@code CDI.current().select(configuredClass).get()}, then
     *       falling back to reflective {@code newInstance()} when CDI
     *       is not up. The reflectively-constructed instance is not
     *       cached.</li>
     *   <li><strong>any other {@code targetType}</strong>: obtains the
     *       active resolver via case 1, enumerates candidates via
     *       {@link ServiceLoader#load(Class)}, and returns
     *       {@link ServicePriorityResolver#resolve(List)} of the
     *       candidates (the head of the priority-sorted list).
     *       Returns {@code null} when no providers are on the
     *       classpath; ports that require exactly one impl document
     *       their own zero-impl failure mode.</li>
     * </ul>
     *
     * @param targetType the SPI port interface to load
     * @param <T>        the SPI port type
     * @return the active provider, or {@code null} if no provider is
     *         on the classpath (case 2 only)
     * @throws IllegalStateException if the {@code ServicePriorityResolver}
     *         bootstrap fails (configured class missing, wrong type,
     *         no public no-arg constructor)
     */
    static <T> T loadService(Class<T> targetType) {
        if (targetType == ServicePriorityResolver.class) {
            return targetType.cast(instantiateConfigured(ServicePriorityResolver.class, true));
        }
        ServicePriorityResolver resolver = loadService(ServicePriorityResolver.class);
        List<T> candidates = new ArrayList<>();
        for (T provider : ServiceLoader.load(targetType)) {
            candidates.add(provider);
        }
        return resolver.resolve(candidates);
    }

    /**
     * Helper for the static accessors: read the FQCN of the configured
     * implementation from MicroProfile Config (key = {@code portType}'s
     * own FQCN, dot-then-underscore fallback applies), reflectively
     * load the class, and instantiate. When {@code tryCdiFirst} is
     * {@code true}, attempt {@code CDI.current().select(...).get()}
     * before reflective instantiation, falling back to a reflective
     * no-arg instance when the container is absent <em>or</em> the bean
     * is not yet resolvable (the bootstrap window) — see the inline note
     * for why that fallback is intentional rather than narrowed.
     */
    private static <T> T instantiateConfigured(Class<T> portType, boolean tryCdiFirst) {
        Config config = ConfigProvider.getConfig();
        String dotKey = portType.getName();
        // Trim before the empty-check so accidental leading/trailing
        // whitespace in a user-supplied microprofile-config.properties
        // value (e.g. ` foo.bar.X `) doesn't reach Class.forName and
        // fail with ClassNotFoundException. A blank-after-trim value
        // is treated as "key not set" so the user gets the same
        // actionable error as if they hadn't configured it at all.
        String configuredClassName = config.getOptionalValue(dotKey, String.class)
                .or(() -> config.getOptionalValue(dotKey.replace('.', '_'), String.class))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElseThrow(() -> new IllegalStateException(
                        "MicroProfile Config key not set: " + dotKey
                                + " (or its underscore variant). Expected the FQCN of a "
                                + portType.getName() + " implementation."));

        Class<?> configuredClass;
        try {
            configuredClass = Class.forName(
                    configuredClassName, true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Configured " + portType.getName() + " class is not on the classpath: "
                            + configuredClassName + " (MP Config key: " + dotKey + ")", e);
        }
        if (!portType.isAssignableFrom(configuredClass)) {
            throw new IllegalStateException(
                    "Configured class " + configuredClassName + " does not implement "
                            + portType.getName() + " (MP Config key: " + dotKey + ")");
        }

        if (tryCdiFirst) {
            try {
                Object cdiInstance = CDI.current().select(configuredClass).get();
                return portType.cast(cdiInstance);
            } catch (RuntimeException cdiUnavailable) {
                // Two legitimate reasons land here, both of which must fall
                // through to the reflective no-arg instance below:
                //   1. The container is not up (pre-container windows) —
                //      CDI.current() itself throws IllegalStateException.
                //   2. The container IS up but the configured bean is not
                //      resolvable yet — most commonly the CDI bootstrap
                //      window (e.g. BeforeBeanDiscovery), where the only
                //      consumer of this path, the @ApplicationScoped default
                //      ServicePriorityResolver, is not a registered bean yet,
                //      so select(...).get() throws UnsatisfiedResolutionException.
                // The catch is intentionally broad: narrowing it to the
                // container-absent signal would let case 2 propagate and break
                // bootstrap-window resolution. For the stateless default
                // resolver the reflective fallback is fully correct. Logged at
                // DEBUG (not swallowed silently) so a genuinely broken custom
                // bean — e.g. an @ApplicationScoped resolver with unsatisfied
                // injection points — is still diagnosable without failing start.
                System.getLogger(TestContext.class.getName()).log(
                        System.Logger.Level.DEBUG,
                        () -> "CDI lookup of " + configuredClassName + " (" + portType.getName()
                                + ") unavailable; falling back to a reflective no-arg instance",
                        cdiUnavailable);
            }
        }

        try {
            Object instance = configuredClass.getDeclaredConstructor().newInstance();
            return portType.cast(instance);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Could not instantiate " + configuredClassName
                            + " via its public no-arg constructor (MP Config key: " + dotKey + ")", e);
        }
    }
}
