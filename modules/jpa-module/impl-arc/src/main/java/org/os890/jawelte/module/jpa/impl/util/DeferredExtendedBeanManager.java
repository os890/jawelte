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

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.inject.spi.BeanManager;

import org.hibernate.resource.beans.container.spi.ExtendedBeanManager;

/**
 * Hibernate {@link ExtendedBeanManager} passed to the EMF via the
 * {@code jakarta.persistence.bean.manager} property so Hibernate's
 * CDI integration sees a {@link BeanManager} reference at EMF
 * bootstrap time but defers actual bean resolution until the
 * runtime {@code BeanManager} is announced.
 *
 * <p>The {@code BeanManager} a CDI {@code Extension} sees during
 * {@code BeforeBeanDiscovery} / {@code AfterBeanDiscovery} is not
 * the final runtime instance — the container hands out a bootstrap-
 * phase reference. Passing that reference straight to Hibernate
 * leaves its CDI integration tied to the bootstrap proxy, which
 * stops resolving once bootstrap completes.
 *
 * <p>{@code JpaCdiExtension} registers an
 * {@code @Observes @Initialized(ApplicationScoped.class)} observer
 * that calls {@link #onBeanManagerInitialized(BeanManager)} once
 * with the final runtime {@code BeanManager}. Any listeners
 * Hibernate registered before that point are fired in
 * registration order; listeners registered later receive the
 * cached reference immediately.
 */
public class DeferredExtendedBeanManager implements ExtendedBeanManager {

    private final List<LifecycleListener> listeners = new ArrayList<>();

    private volatile BeanManager runtimeBeanManager;

    /** No-arg constructor used by the CDI extension. */
    public DeferredExtendedBeanManager() {
    }

    @Override
    public synchronized void registerLifecycleListener(LifecycleListener lifecycleListener) {
        if (runtimeBeanManager != null) {
            // Already past the "BeanManager ready" event — fire
            // immediately so a late-registering listener doesn't miss
            // the notification.
            lifecycleListener.beanManagerInitialized(runtimeBeanManager);
            return;
        }
        listeners.add(lifecycleListener);
    }

    /**
     * Called once by {@code JpaCdiExtension}'s
     * {@code @Initialized(ApplicationScoped.class)} observer with the
     * final runtime {@link BeanManager}. Notifies every previously
     * registered listener in registration order, then caches the
     * reference so any subsequent
     * {@link #registerLifecycleListener(LifecycleListener)} call is
     * fired immediately.
     *
     * @param beanManager the final runtime {@code BeanManager}
     */
    public synchronized void onBeanManagerInitialized(BeanManager beanManager) {
        if (runtimeBeanManager != null) {
            return;
        }
        runtimeBeanManager = beanManager;
        for (LifecycleListener listener : listeners) {
            listener.beanManagerInitialized(beanManager);
        }
    }
}
