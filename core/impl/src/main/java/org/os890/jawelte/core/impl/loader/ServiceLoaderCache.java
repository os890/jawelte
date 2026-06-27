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
package org.os890.jawelte.core.impl.loader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

import org.os890.jawelte.core.api.port.ServicePriorityResolver;
import org.os890.jawelte.core.api.port.TestBeanContainerPort;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;
import org.os890.jawelte.core.impl.adapter.spi.DefaultServicePriorityResolver;

/**
 * Per-classloader cache of the SPI implementations the delegating
 * extension forwards to. The classpath does not change between test
 * classes within a single JVM, so each SPI is resolved at most once
 * per classloader (lazy on first use, double-checked locking).
 *
 * <p>{@link TestBeanContainerPort} requires exactly one implementation
 * on the classpath; zero or multiple implementations result in an
 * {@link IllegalStateException} with the messages mandated by
 * TICKET-001's SPI section.
 *
 * <p>{@link TestModuleLifecyclePort} allows zero or more
 * implementations; the cached list is ordered by the active
 * {@link ServicePriorityResolver} (obtained via
 * {@link TestContext#loadService(Class)}), exactly like every other
 * prioritized SPI in the project — ascending {@code @Priority} with
 * the class-name tiebreak, and any custom resolver installed via MP
 * Config applies here too. Implementations without {@code @Priority}
 * sort last.
 */
public abstract class ServiceLoaderCache {

    private static volatile TestBeanContainerPort cachedContainerPort;
    private static volatile List<TestModuleLifecyclePort> cachedLifecyclePorts;

    /**
     * Suppressed-instantiation constructor. The class is
     * {@code abstract} so direct {@code new} is impossible; the
     * explicit declaration silences {@code javadoc -doclint:all} on
     * the otherwise synthesized default constructor.
     */
    protected ServiceLoaderCache() {
    }

    /**
     * Resolve and cache the single {@link TestBeanContainerPort}
     * implementation on the classpath.
     *
     * @return the single implementation
     * @throws IllegalStateException if zero or more than one
     *         implementation is found
     */
    public static TestBeanContainerPort resolveContainerPort() {
        TestBeanContainerPort local = cachedContainerPort;
        if (local == null) {
            synchronized (ServiceLoaderCache.class) {
                local = cachedContainerPort;
                if (local == null) {
                    local = loadSingletonContainerPort();
                    cachedContainerPort = local;
                }
            }
        }
        return local;
    }

    /**
     * Resolve and cache all {@link TestModuleLifecyclePort}
     * implementations on the classpath, ordered by the active
     * {@link ServicePriorityResolver}.
     *
     * @return an unmodifiable, priority-sorted list (possibly empty)
     */
    public static List<TestModuleLifecyclePort> resolveLifecyclePorts() {
        List<TestModuleLifecyclePort> local = cachedLifecyclePorts;
        if (local == null) {
            synchronized (ServiceLoaderCache.class) {
                local = cachedLifecyclePorts;
                if (local == null) {
                    local = loadAndSortLifecyclePorts();
                    cachedLifecyclePorts = local;
                }
            }
        }
        return local;
    }

    private static TestBeanContainerPort loadSingletonContainerPort() {
        List<TestBeanContainerPort> providers = new ArrayList<>();
        Iterator<TestBeanContainerPort> iterator =
                ServiceLoader.load(TestBeanContainerPort.class).iterator();
        while (iterator.hasNext()) {
            providers.add(iterator.next());
        }

        if (providers.isEmpty()) {
            throw new IllegalStateException(
                    "No TestBeanContainerPort found via ServiceLoader. "
                            + "Add cdi-module or quarkus-module to the test classpath.");
        }
        if (providers.size() > 1) {
            throw new IllegalStateException(
                    "Multiple TestBeanContainerPort implementations found: ["
                            + providers.get(0).getClass().getName()
                            + ", "
                            + providers.get(1).getClass().getName()
                            + "]. Exactly one is required.");
        }
        return providers.get(0);
    }

    private static List<TestModuleLifecyclePort> loadAndSortLifecyclePorts() {
        List<TestModuleLifecyclePort> providers = new ArrayList<>();
        Iterator<TestModuleLifecyclePort> iterator =
                ServiceLoader.load(TestModuleLifecyclePort.class).iterator();
        while (iterator.hasNext()) {
            providers.add(iterator.next());
        }
        return Collections.unmodifiableList(sortByActiveResolver(providers));
    }

    /**
     * Order providers through the active {@link ServicePriorityResolver}
     * (same single source of truth as {@link TestContext#loadService(Class)}),
     * so the class-name tiebreak and any custom resolver apply to lifecycle
     * ordering too.
     *
     * <p>MicroProfile Config is a {@code provided}-scope dependency, so a
     * minimal runtime may not have it on the classpath — and the resolver is
     * selected via MP Config. When it (or the resolver) cannot be loaded, fall
     * back to the built-in {@link DefaultServicePriorityResolver}: it applies
     * the same ascending-{@code @Priority} + class-name-tiebreak rule, and a
     * custom resolver isn't expressible without MP Config anyway.
     */
    private static List<TestModuleLifecyclePort> sortByActiveResolver(
            List<TestModuleLifecyclePort> providers) {
        ServicePriorityResolver resolver;
        try {
            resolver = TestContext.loadService(ServicePriorityResolver.class);
        } catch (RuntimeException | LinkageError microProfileConfigUnavailable) {
            resolver = new DefaultServicePriorityResolver();
        }
        return resolver.sort(providers);
    }
}
