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

import org.os890.jawelte.module.scope.api.TestClassScoped;

/**
 * CDI {@code Context} for {@code @TestClassScoped}. Thin delegate
 * over {@link TestClassScopeStore}; the store holds the live bean
 * map, this class wires it up to CDI.
 *
 * <p>Mirrors {@code @ApplicationScoped} with a per-test-class
 * lifetime. The store's underlying map is allocated in its
 * constructor (during {@code AfterBeanDiscovery}), so this context
 * is "live" from the moment {@code TestScopeCdiExtension} constructs
 * it. {@link #isActive()} always returns {@code true} while the CDI
 * container is up — there is no separate activation step;
 * {@code ScopeLifecycleAdapter.beforeAll} is a no-op for this
 * context. Deactivation runs once in
 * {@code ScopeLifecycleAdapter.afterAll}, before cdi-module's
 * {@code TestBeanContainerPort.afterAll} closes the
 * {@code SeContainer}.
 *
 * <p>Implements {@link AlterableContext} so user code that calls
 * {@code Instance#destroy(Object)} on a class-scoped bean takes
 * effect immediately rather than waiting for {@code afterAll}.
 */
public class TestClassScopedContext implements AlterableContext {

    private final TestClassScopeStore store;

    /**
     * Construct the context over the given store.
     *
     * @param store the bean store this context delegates to
     */
    public TestClassScopedContext(TestClassScopeStore store) {
        this.store = store;
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return TestClassScoped.class;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        Map<Contextual<?>, ScopedBeanInstance<?>> beans = store.getOrCreateMap();
        ScopedBeanInstance<?> existing = beans.computeIfAbsent(contextual,
                key -> {
                    Contextual<T> typedKey = (Contextual<T>) key;
                    T instance = typedKey.create(creationalContext);
                    return new ScopedBeanInstance<>(instance, creationalContext);
                });
        return (T) existing.instance();
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
     * Destroy every bean in the current store and null the map
     * reference. Called by {@code ScopeLifecycleAdapter.afterAll}
     * before cdi-module closes the {@code SeContainer}.
     */
    public void deactivate() {
        store.destroyAll();
    }
}
