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
import java.util.Map;
import java.util.Optional;
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
 * <p>{@link #getOrCreate(String, Supplier)} atomically returns the
 * cached instance or invokes the supplied factory function on cache
 * miss; the caller chooses whether the new factory is built via the
 * spec
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
     * cache miss. Subsequent calls return the same instance
     * regardless of the supplied function — the cache is keyed by
     * name only.
     *
     * @param persistenceUnitName the persistence unit name
     * @param entityManagerFactorySupplier a function that builds the
     *                            {@code EntityManagerFactory} on
     *                            cache miss
     * @return the cached {@code EntityManagerFactory}
     */
    public static EntityManagerFactory getOrCreate(
            String persistenceUnitName, Supplier<EntityManagerFactory> entityManagerFactorySupplier) {
        registerShutdownHookOnce();
        return CACHE.computeIfAbsent(persistenceUnitName, name -> entityManagerFactorySupplier.get());
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

    private static void registerShutdownHookOnce() {
        if (SHUTDOWN_HOOK_REGISTERED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(EmfCache::closeAll, "jawelte-emf-cache-shutdown"));
        }
    }

    private static void closeAll() {
        for (Map.Entry<String, EntityManagerFactory> entry : CACHE.entrySet()) {
            try {
                entry.getValue().close();
            } catch (RuntimeException loggedAndIgnored) {
                LOG.log(Level.WARNING,
                        "Failed to close EntityManagerFactory for persistence unit '"
                                + entry.getKey() + "' on shutdown",
                        loggedAndIgnored);
            }
        }
    }
}
