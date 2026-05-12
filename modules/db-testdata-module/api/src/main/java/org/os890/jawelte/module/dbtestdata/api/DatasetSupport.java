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
package org.os890.jawelte.module.dbtestdata.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.os890.jawelte.core.api.port.ServicePriorityResolver;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.dbtestdata.api.port.DbDiffEngine;
import org.os890.jawelte.module.dbtestdata.api.port.DbSeedEngine;
import org.os890.jawelte.module.dbtestdata.api.port.ELInterpolator;

/**
 * Package-private bag of static helpers shared by
 * {@link DbSeedBuilder} and {@link DbDiffBuilder}. Three concerns
 * live here:
 *
 * <ul>
 *   <li>classpath-resource loading;</li>
 *   <li>per-format engine resolution with per-format caching (one
 *       {@link DbSeedEngine} / {@link DbDiffEngine} per
 *       {@link DbSeedEngine#format()} value, picked by the project-
 *       wide {@link ServicePriorityResolver} obtained through
 *       {@link TestContext#loadService(Class)});</li>
 *   <li>active-{@link ELInterpolator} resolution, cached for the JVM
 *       lifetime.</li>
 * </ul>
 *
 * <p>The class is not part of the api contract — it sits at
 * package-private visibility to keep the entry-point types
 * ({@link DbSeed}, {@link DbDiffBuilder}, …) free of resolution
 * plumbing.
 */
class DatasetSupport {

    /** Default dataset format identifier used when the builder omits {@code format(...)}. */
    static final String DEFAULT_FORMAT = "dbunit-xml";

    private static final ConcurrentMap<String, DbSeedEngine> CACHED_SEED_ENGINES = new ConcurrentHashMap<>();

    private static final ConcurrentMap<String, DbDiffEngine> CACHED_DIFF_ENGINES = new ConcurrentHashMap<>();

    private static volatile ELInterpolator cachedInterpolator;

    private DatasetSupport() {
    }

    /**
     * Load {@code classpathResource} via the thread context
     * classloader. Throws {@link IllegalArgumentException} when the
     * resource is missing — matches the api error contract.
     *
     * @param classpathResource the resource path
     * @return the UTF-8 text of the resource
     */
    static String loadClasspathResource(String classpathResource) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(classpathResource)) {
            if (stream == null) {
                throw new IllegalArgumentException("Resource not found: " + classpathResource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ioException) {
            throw new IllegalArgumentException(
                    "Failed to read classpath resource: " + classpathResource, ioException);
        }
    }

    /**
     * Resolve the active {@link DbSeedEngine} for {@code format}.
     * Filters every {@link ServiceLoader}-discovered engine by its
     * {@link DbSeedEngine#format()} value, then hands the matches to
     * the project-wide {@link ServicePriorityResolver}.
     *
     * @param format the format identifier the builder picked
     * @return the active engine for that format
     * @throws IllegalArgumentException when no engine claims the
     *         requested format
     */
    static DbSeedEngine resolveSeedEngine(String format) {
        DbSeedEngine cached = CACHED_SEED_ENGINES.get(format);
        if (cached != null) {
            return cached;
        }
        List<DbSeedEngine> matching = new ArrayList<>();
        for (DbSeedEngine candidate : ServiceLoader.load(DbSeedEngine.class)) {
            if (format.equals(candidate.format())) {
                matching.add(candidate);
            }
        }
        if (matching.isEmpty()) {
            throw new IllegalArgumentException("Unknown dataset format: " + format);
        }
        ServicePriorityResolver resolver = TestContext.loadService(ServicePriorityResolver.class);
        DbSeedEngine resolved = resolver.resolve(matching);
        CACHED_SEED_ENGINES.put(format, resolved);
        return resolved;
    }

    /**
     * Mirror of {@link #resolveSeedEngine(String)} for the diff side.
     *
     * @param format the format identifier the builder picked
     * @return the active engine for that format
     * @throws IllegalArgumentException when no engine claims the
     *         requested format
     */
    static DbDiffEngine resolveDiffEngine(String format) {
        DbDiffEngine cached = CACHED_DIFF_ENGINES.get(format);
        if (cached != null) {
            return cached;
        }
        List<DbDiffEngine> matching = new ArrayList<>();
        for (DbDiffEngine candidate : ServiceLoader.load(DbDiffEngine.class)) {
            if (format.equals(candidate.format())) {
                matching.add(candidate);
            }
        }
        if (matching.isEmpty()) {
            throw new IllegalArgumentException("Unknown dataset format: " + format);
        }
        ServicePriorityResolver resolver = TestContext.loadService(ServicePriorityResolver.class);
        DbDiffEngine resolved = resolver.resolve(matching);
        CACHED_DIFF_ENGINES.put(format, resolved);
        return resolved;
    }

    /**
     * Resolve and cache the active {@link ELInterpolator}. The
     * double-checked-locking pattern keeps lookups lock-free after
     * the first call.
     *
     * @return the cached active interpolator
     * @throws IllegalStateException when no interpolator is on the
     *         classpath
     */
    static ELInterpolator resolveInterpolator() {
        ELInterpolator local = cachedInterpolator;
        if (local != null) {
            return local;
        }
        synchronized (DatasetSupport.class) {
            local = cachedInterpolator;
            if (local != null) {
                return local;
            }
            List<ELInterpolator> matching = new ArrayList<>();
            for (ELInterpolator candidate : ServiceLoader.load(ELInterpolator.class)) {
                matching.add(candidate);
            }
            if (matching.isEmpty()) {
                throw new IllegalStateException(
                        "No ELInterpolator registered — was db-testdata-module/impl included?");
            }
            ServicePriorityResolver resolver = TestContext.loadService(ServicePriorityResolver.class);
            local = resolver.resolve(matching);
            cachedInterpolator = local;
            return local;
        }
    }
}
