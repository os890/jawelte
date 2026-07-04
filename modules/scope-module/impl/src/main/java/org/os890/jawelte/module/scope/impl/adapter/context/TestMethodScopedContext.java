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

import java.lang.annotation.Annotation;
import java.util.Map;

import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;

import org.os890.jawelte.module.scope.api.TestMethodScoped;

/**
 * CDI {@code Context} for {@code @TestMethodScoped}. Thin delegate
 * over {@link TestMethodScopeStore}; the store holds the live bean
 * map, this class wires it up to CDI.
 *
 * <p>Mirrors {@code @ApplicationScoped} with a per-test-method
 * lifetime. {@link #isActive()} reflects the store's allocation: the
 * {@code ScopeLifecycleAdapter} allocates the store in {@code beforeEach}
 * and tears it down in {@code afterEach}. If an observer vetoes the
 * {@code BeforeScopeStarted} event fired by {@code beforeEach} (e.g. via
 * {@code @TestControl(startScopes=…)}), the adapter leaves the store
 * unallocated, {@link #isActive()} stays {@code false}, and bean access
 * throws {@code ContextNotActiveException} — as the
 * {@code BeforeScopeStarted} contract promises.
 *
 * <p>Implements {@link AlterableContext} so user code that calls
 * {@code Instance#destroy(Object)} on a method-scoped bean takes
 * effect — typical for tests that want to force a {@code @PreDestroy}
 * mid-method.
 */
public class TestMethodScopedContext implements AlterableContext {

    private final TestMethodScopeStore store;

    /**
     * Construct the context over the given store.
     *
     * @param store the bean store this context delegates to
     */
    public TestMethodScopedContext(TestMethodScopeStore store) {
        this.store = store;
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return TestMethodScoped.class;
    }

    @Override
    public boolean isActive() {
        // Active only while the store's map is allocated — the lifecycle
        // adapter allocates it in beforeEach (unless the BeforeScopeStarted
        // event was vetoed) and tears it down in afterEach. When
        // @TestControl(startScopes=…) vetoes @TestMethodScoped, the store
        // stays unallocated, so bean access throws ContextNotActiveException
        // as the BeforeScopeStarted contract promises.
        return store.isAllocated();
    }

    @Override
    public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        // Delegates to the store, which serializes creation per
        // Contextual (not via Map.computeIfAbsent) so a bean whose
        // creation injects another @TestMethodScoped bean can re-enter
        // safely. See ScopeStore.getOrCreate.
        return store.getOrCreate(contextual, creationalContext);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Contextual<T> contextual) {
        Map<Contextual<?>, ScopedBeanInstance<?>> beans = store.map();
        if (beans == null) {
            return null;
        }
        ScopedBeanInstance<?> existing = beans.get(contextual);
        return existing == null ? null : (T) existing.instance();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void destroy(Contextual<?> contextual) {
        Map<Contextual<?>, ScopedBeanInstance<?>> beans = store.map();
        if (beans == null) {
            return;
        }
        ScopedBeanInstance<?> removed = beans.remove(contextual);
        if (removed == null) {
            return;
        }
        Contextual c = contextual;
        c.destroy(removed.instance(), removed.creationalContext());
    }

    /**
     * Allocate a fresh bean map, making the context active. Called by
     * {@code ScopeLifecycleAdapter.beforeEach} unless the
     * {@code BeforeScopeStarted} event was vetoed for this method — in which
     * case activation is skipped and {@link #isActive()} stays {@code false}.
     */
    public void activate() {
        store.allocate();
    }

    /**
     * Destroy every bean in the current store and drop the map, making the
     * context inactive. Called by {@code ScopeLifecycleAdapter.afterEach}.
     */
    public void deactivate() {
        store.destroyAll();
    }
}
