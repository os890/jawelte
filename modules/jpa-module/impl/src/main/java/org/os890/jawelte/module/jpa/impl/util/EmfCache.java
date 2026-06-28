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
package org.os890.jawelte.module.jpa.impl.util;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import jakarta.persistence.EntityManagerFactory;

/**
 * JVM-wide cache of {@link EntityManagerFactory} instances keyed by
 * persistence unit name. {@code EntityManagerFactory} bootstrap is
 * heavy (schema generation, metamodel build, connection pool
 * warm-up); reusing the same instance across every test class is
 * the main jpa-module performance win.
 *
 * <p>{@link #getOrCreate(String, Map, Supplier)} atomically returns
 * the cached instance or invokes the supplied factory function on
 * cache miss; the caller chooses whether the new factory is built via
 * the spec
 * {@code Persistence.createEntityManagerFactory(name, props)} path
 * or via Hibernate's
 * {@code HibernatePersistenceProvider.createContainerEntityManagerFactory(unitInfo, props)}
 * path with a custom {@code PersistenceUnitInfo} (the latter is
 * required for our auto-discovery flow because Hibernate does not
 * scan for {@code @Entity} classes outside of an application
 * server). A JVM shutdown hook is registered on first use; the
 * hook closes every cached factory in an individual try/catch and
 * logs failures at {@link Level#WARNING} without propagating.
 */
public abstract class EmfCache {

    private static final Logger LOG = System.getLogger(EmfCache.class.getName());

    private static final Map<String, EntityManagerFactory> CACHE = new ConcurrentHashMap<>();

    /**
     * Per-PU snapshot of the configuration that bootstrapped the cached
     * {@link EntityManagerFactory}, used to fail fast when a later
     * caller asks for the same PU name with a divergent configuration.
     * Only stable, value-typed properties are recorded (object-valued
     * entries such as {@code jakarta.persistence.bean.manager} are
     * per-bootstrap references, not configuration). Kept in lock-step
     * with {@link #CACHE} by every mutator.
     */
    private static final Map<String, Map<String, String>> CACHED_CONFIG = new ConcurrentHashMap<>();

    private static final AtomicBoolean SHUTDOWN_HOOK_REGISTERED = new AtomicBoolean(false);

    /**
     * Suppressed-instantiation constructor. The class is
     * {@code abstract} so direct {@code new} is impossible; the
     * explicit declaration silences {@code javadoc -doclint:all} on
     * the otherwise synthesized default constructor.
     */
    protected EmfCache() {
    }

    /**
     * Return the cached {@link EntityManagerFactory} for the given
     * persistence unit, invoking the supplied factory function on
     * cache miss. The cache is keyed by name only and is reused across
     * test classes for the whole JVM lifetime — bootstrapping an
     * {@code EntityManagerFactory} is heavy, so this reuse is the main
     * jpa-module performance win.
     *
     * <p>Because the key is the name alone, two test classes that
     * declare the same persistence-unit name must resolve to the
     * <strong>same</strong> configuration; otherwise the second class
     * would silently run against the first class's factory (and, in the
     * in-memory case, the first class's database). To make that mistake
     * loud instead of silent, this method records the resolved
     * configuration on cache miss and, on a subsequent hit, throws an
     * {@link IllegalStateException} if {@code resolvedProperties}
     * differs from what bootstrapped the cached factory. Identical
     * configuration (the normal case) reuses the cached factory as
     * before. The comparison ignores object-valued entries (e.g.
     * {@code jakarta.persistence.bean.manager}), which are per-bootstrap
     * references rather than configuration.
     *
     * @param persistenceUnitName the persistence unit name
     * @param resolvedProperties  the fully resolved EMF properties for
     *                            this request; used to detect a
     *                            divergent same-name configuration
     * @param entityManagerFactorySupplier a function that builds the
     *                            {@code EntityManagerFactory} on
     *                            cache miss
     * @return the cached {@code EntityManagerFactory}
     * @throws IllegalStateException if the persistence unit is already
     *         cached in this JVM with a different resolved configuration
     */
    public static EntityManagerFactory getOrCreate(
            String persistenceUnitName,
            Map<String, Object> resolvedProperties,
            Supplier<EntityManagerFactory> entityManagerFactorySupplier) {
        registerShutdownHookOnce();
        Map<String, String> requestedConfig = configSnapshot(resolvedProperties);
        EntityManagerFactory existing = CACHE.get(persistenceUnitName);
        if (existing != null) {
            Map<String, String> cachedConfig = CACHED_CONFIG.getOrDefault(persistenceUnitName, Map.of());
            if (!cachedConfig.equals(requestedConfig)) {
                throw new IllegalStateException(
                        "Persistence unit '" + persistenceUnitName + "' is already bootstrapped in this JVM "
                                + "with a different configuration. The EntityManagerFactory cache is keyed by "
                                + "persistence-unit name only (reused across test classes for performance), so "
                                + "two test classes that declare the same persistence unit must resolve to the "
                                + "identical configuration. Differing keys: "
                                + differingKeys(cachedConfig, requestedConfig) + ". Use distinct persistence-unit "
                                + "names, or align the @PersistenceConfig / persistence-property.* configuration "
                                + "across the classes.");
            }
            return existing;
        }
        return CACHE.computeIfAbsent(persistenceUnitName, name -> {
            long startNanos = System.nanoTime();
            LOG.log(Level.INFO,
                    "Bootstrapping EntityManagerFactory for persistence unit '" + name + "'");
            EntityManagerFactory factory = entityManagerFactorySupplier.get();
            CACHED_CONFIG.put(name, requestedConfig);
            long durationMillis = (System.nanoTime() - startNanos) / 1_000_000L;
            LOG.log(Level.INFO,
                    "Bootstrapped EntityManagerFactory for persistence unit '" + name
                            + "' in " + durationMillis + "ms");
            return factory;
        });
    }

    /**
     * Snapshot the stable, value-typed configuration entries that
     * define an {@link EntityManagerFactory}, dropping object-valued
     * entries (e.g. the deferred {@code jakarta.persistence.bean.manager})
     * that change every bootstrap and are not configuration.
     */
    private static Map<String, String> configSnapshot(Map<String, Object> resolvedProperties) {
        Map<String, String> snapshot = new TreeMap<>();
        for (Map.Entry<String, Object> entry : resolvedProperties.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String || value instanceof Number
                    || value instanceof Boolean || value instanceof Character) {
                snapshot.put(entry.getKey(), String.valueOf(value));
            }
        }
        return snapshot;
    }

    /**
     * The sorted, comma-joined set of keys whose values differ between
     * the cached and requested configurations (keys present in only one
     * side count as differing). Reports keys only, never values, so a
     * password or URL is not leaked into the exception message.
     */
    private static String differingKeys(Map<String, String> cached, Map<String, String> requested) {
        Set<String> allKeys = new TreeSet<>();
        allKeys.addAll(cached.keySet());
        allKeys.addAll(requested.keySet());
        List<String> differing = new ArrayList<>();
        for (String key : allKeys) {
            if (!Objects.equals(cached.get(key), requested.get(key))) {
                differing.add(key);
            }
        }
        return String.join(", ", differing);
    }

    /**
     * Look up the cached {@link EntityManagerFactory} for the given
     * persistence unit without creating a new one.
     *
     * @param persistenceUnitName the persistence unit name
     * @return the cached {@code EntityManagerFactory}, or
     *         {@link Optional#empty()} if no entry exists
     */
    public static Optional<EntityManagerFactory> getCached(String persistenceUnitName) {
        return Optional.ofNullable(CACHE.get(persistenceUnitName));
    }

    /**
     * Close and remove the cached {@link EntityManagerFactory} for
     * the given persistence unit, if one exists. Used by
     * {@code JpaLifecycleAdapter.afterAll} in file mode so the H2
     * file lock is released for the next test class. Idempotent —
     * a missing entry is a silent no-op. Close failures are logged
     * at {@link Level#WARNING} and not propagated.
     *
     * @param persistenceUnitName the persistence unit name to evict
     */
    public static void evict(String persistenceUnitName) {
        EntityManagerFactory removed = CACHE.remove(persistenceUnitName);
        CACHED_CONFIG.remove(persistenceUnitName);
        if (removed == null) {
            return;
        }
        try {
            if (removed.isOpen()) {
                removed.close();
            }
        } catch (RuntimeException loggedAndIgnored) {
            LOG.log(Level.WARNING,
                    "Failed to close evicted EntityManagerFactory for persistence unit '"
                            + persistenceUnitName + "'",
                    loggedAndIgnored);
        }
    }

    /**
     * Close every cached {@link EntityManagerFactory} and empty the
     * cache. Invoked by the JVM shutdown hook (registered on first
     * use) and — when the consumer opts in — by
     * {@code JpaLauncherSessionListener.launcherSessionClosed} for
     * deterministic JVM-scoped cleanup before the shutdown hook runs.
     * Idempotent: a second call after the cache is empty is a no-op.
     * Close failures are aggregated into a single primary
     * {@link RuntimeException} chain and logged at
     * {@link Level#WARNING}; never rethrown so shutdown hooks and
     * launcher-session listeners can't break on residual cleanup
     * failures.
     */
    public static void closeAll() {
        RuntimeException primary = null;
        // Iterate by sorted PU name. CACHE is a ConcurrentHashMap whose
        // iteration order is unspecified — without sorting the "first
        // failure becomes primary" rule resolves to whichever PU the
        // map iterator happens to hit first, and that's non-deterministic
        // across runs. Sorting by name gives a stable primary across
        // runs (alphabetically first failing PU wins).
        List<String> persistenceUnitNames = new ArrayList<>(CACHE.keySet());
        Collections.sort(persistenceUnitNames);
        for (String persistenceUnitName : persistenceUnitNames) {
            EntityManagerFactory factory = CACHE.get(persistenceUnitName);
            if (factory == null) {
                continue;
            }
            try {
                factory.close();
            } catch (RuntimeException closeFailure) {
                RuntimeException wrapped = new RuntimeException(
                        "Failed to close EntityManagerFactory for persistence unit '"
                                + persistenceUnitName + "' on shutdown",
                        closeFailure);
                if (primary == null) {
                    primary = wrapped;
                } else {
                    primary.addSuppressed(wrapped);
                }
            }
        }
        CACHE.clear();
        CACHED_CONFIG.clear();
        if (primary != null) {
            // Aggregate per the project-wide TICKET-001 exception policy:
            // first failure is the primary cause, every subsequent failure
            // rides along as a suppressed exception. Logged once instead of
            // one WARNING per failure so log readers see one trace + chain.
            // Not rethrown — JVM shutdown hooks swallow throwables.
            LOG.log(Level.WARNING, "EntityManagerFactory close failures during JVM shutdown", primary);
        }
    }

    private static void registerShutdownHookOnce() {
        if (SHUTDOWN_HOOK_REGISTERED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(EmfCache::closeAll, "jawelte-emf-cache-shutdown"));
        }
    }
}
