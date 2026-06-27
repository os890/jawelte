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
package org.os890.jawelte.module.scope.impl.adapter.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;

/**
 * Bean-store base for jawelte's test-lifecycle CDI scopes. Holds the
 * per-scope {@code Map<Contextual<?>, ScopedBeanInstance<?>>}; the
 * {@code Context} implementations are thin delegates over this store.
 *
 * <p>A store is bound on {@link org.os890.jawelte.core.api.port.TestContext}
 * via {@code bindMetadata}, so introspection callers (custom
 * assertions, debugging tools, observers from other modules) can
 * reach the live bean map without going through the CDI
 * {@code BeanManager}. The {@link #map()} accessor exposes
 * {@code Map} on the SPI surface — the underlying
 * {@link ConcurrentHashMap} is an implementation detail.
 *
 * <p>Subclasses differ in whether the map is allocated eagerly (in
 * the constructor, mirroring {@code @ApplicationScoped} —
 * {@link TestClassScopeStore}) or lazily / per-method
 * ({@link TestMethodScopeStore}).
 */
public abstract class ScopeStore {

    private volatile Map<Contextual<?>, ScopedBeanInstance<?>> map;

    /**
     * Subclass-only constructor.
     */
    protected ScopeStore() {
    }

    /**
     * Whether the store currently holds a live map. {@code false}
     * after a {@link #destroyAll()} until the next allocation.
     *
     * @return {@code true} when the underlying map is non-null
     */
    public boolean isAllocated() {
        return this.map != null;
    }

    /**
     * The live bean map, or {@code null} when the store has been
     * deactivated and not yet reallocated. Returned reference is the
     * underlying {@link ConcurrentHashMap}; callers may iterate and
     * read from it concurrently.
     *
     * @return the live map, or {@code null}
     */
    public Map<Contextual<?>, ScopedBeanInstance<?>> map() {
        return this.map;
    }

    /**
     * Returns the live bean map, allocating a fresh one if the store
     * is currently empty. Backs the lazy "first dereference creates
     * the store" path on test-method-scoped beans accessed outside a
     * test method (e.g. from {@code @BeforeAll}).
     *
     * @return a non-{@code null} live map
     */
    public Map<Contextual<?>, ScopedBeanInstance<?>> getOrCreateMap() {
        Map<Contextual<?>, ScopedBeanInstance<?>> local = this.map;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (this.map == null) {
                this.map = new ConcurrentHashMap<>();
            }
            return this.map;
        }
    }

    /**
     * Return the live contextual instance for {@code contextual},
     * creating and storing it on first request. Creation is
     * serialized per {@link Contextual} via the contextual's own
     * monitor with a double-check — deliberately NOT through
     * {@code Map.computeIfAbsent}.
     *
     * <p>{@code Contextual.create()} runs the bean's constructor and
     * {@code @Inject} resolution, which may resolve ANOTHER bean in
     * this same scope (the cdi/wiremock/ejb/config remaps all land in
     * one store and can inject one another). Here that nested request
     * is a plain {@code get}/{@code put} on the
     * {@link ConcurrentHashMap}, so it never re-enters
     * {@code computeIfAbsent} from within its own mapping function —
     * which {@code ConcurrentHashMap} forbids (it throws
     * {@code IllegalStateException("Recursive update")} for a colliding
     * bin and is undefined otherwise). {@code create()} runs while
     * holding only the per-{@code Contextual} monitor, never a
     * map-structural lock.
     *
     * <p>Concurrent first access for the SAME {@code Contextual} still
     * constructs exactly once: contending threads block on the
     * monitor and observe the stored instance after the double-check
     * (so {@code @PostConstruct} fires once — see scope-module test
     * scenarios 09 / 10). Distinct {@code Contextual}s use distinct
     * monitors, so unrelated beans still create concurrently.
     *
     * @param contextual        the bean to obtain
     * @param creationalContext the creational context to create from
     * @param <T>               the bean type
     * @return the live (possibly freshly created) instance
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrCreate(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        Map<Contextual<?>, ScopedBeanInstance<?>> beans = getOrCreateMap();
        ScopedBeanInstance<?> existing = beans.get(contextual);
        if (existing != null) {
            return (T) existing.instance();
        }
        synchronized (contextual) {
            existing = beans.get(contextual);
            if (existing != null) {
                return (T) existing.instance();
            }
            T instance = contextual.create(creationalContext);
            beans.put(contextual, new ScopedBeanInstance<>(instance, creationalContext));
            return instance;
        }
    }

    /**
     * Replace the underlying map with a fresh {@link ConcurrentHashMap}.
     * Called by the lifecycle adapter in {@code beforeEach} on
     * test-method-scoped stores and by
     * {@link TestClassScopeStore}'s constructor on test-class-scoped
     * stores.
     */
    public void allocate() {
        synchronized (this) {
            this.map = new ConcurrentHashMap<>();
        }
    }

    /**
     * Destroy every entry in the map via
     * {@link Contextual#destroy(Object, jakarta.enterprise.context.spi.CreationalContext)}
     * and null the map reference. Exceptions are aggregated per the
     * project-wide TICKET-001 policy: the first thrown becomes the
     * primary, the rest are attached via
     * {@link Throwable#addSuppressed(Throwable)}, and the aggregate
     * is rethrown after the loop. The map reference is nulled
     * unconditionally in a {@code finally} so a re-entrant
     * {@code destroyAll()} does not double-destroy.
     */
    public void destroyAll() {
        Map<Contextual<?>, ScopedBeanInstance<?>> snapshot;
        synchronized (this) {
            snapshot = this.map;
        }
        if (snapshot == null) {
            return;
        }
        List<Throwable> collected = new ArrayList<>();
        try {
            // Iterate by Contextual class FQN. The underlying map is a
            // ConcurrentHashMap whose iteration order is unspecified —
            // without sorting, @PreDestroy callback dispatch order +
            // the "first thrown becomes primary" rule resolve to
            // whichever Contextual the iterator happens to hit first,
            // and that's non-deterministic across runs. Sorting by
            // FQN gives stable order regardless of bean-creation
            // sequence.
            List<Map.Entry<Contextual<?>, ScopedBeanInstance<?>>> ordered =
                    new ArrayList<>(snapshot.entrySet());
            ordered.sort(Comparator.comparing(entry -> entry.getKey().getClass().getName()));
            for (Map.Entry<Contextual<?>, ScopedBeanInstance<?>> entry : ordered) {
                try {
                    destroyEntry(entry);
                } catch (RuntimeException | Error failure) {
                    collected.add(failure);
                }
            }
        } finally {
            synchronized (this) {
                this.map = null;
            }
        }
        rethrowAggregated(collected);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void destroyEntry(Map.Entry<Contextual<?>, ScopedBeanInstance<?>> entry) {
        Contextual contextual = entry.getKey();
        ScopedBeanInstance<?> beanInstance = entry.getValue();
        contextual.destroy(beanInstance.instance(), beanInstance.creationalContext());
    }

    private static void rethrowAggregated(List<Throwable> collected) {
        if (collected.isEmpty()) {
            return;
        }
        Throwable primary = collected.get(0);
        for (int i = 1; i < collected.size(); i++) {
            Throwable next = collected.get(i);
            if (next != primary) {
                primary.addSuppressed(next);
            }
        }
        if (primary instanceof RuntimeException re) {
            throw re;
        }
        if (primary instanceof Error err) {
            throw err;
        }
        throw new RuntimeException(primary);
    }
}
