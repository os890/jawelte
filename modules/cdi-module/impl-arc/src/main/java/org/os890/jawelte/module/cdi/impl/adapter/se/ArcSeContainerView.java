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
package org.os890.jawelte.module.cdi.impl.adapter.se;

import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.stream.Stream;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.util.TypeLiteral;

import io.quarkus.arc.Arc;

/**
 * Read-only {@link SeContainer} view over the currently active ArC
 * container. Used by cdi-module's {@code CdiTestBeanContainer} to
 * publish an {@code SeContainer} on {@code TestContext} after a
 * jawelte-managed ArC bootstrap — the existing
 * {@link ArcSeContainerInitializer} returns an {@code SeContainer}
 * whose {@code close()} shuts ArC down, which is the right semantic
 * for the user-managed (Jakarta SE) path but the WRONG semantic when
 * jawelte owns the container (lifecycle ports tearing down their own
 * state via {@code SeContainer#close} during {@code afterAll} would
 * race with cdi-module's own {@code afterAll}).
 *
 * <p>{@link #close()} is therefore a no-op on this view; cdi-module's
 * {@code afterAll} still calls {@code Arc.shutdown()}.
 */
public class ArcSeContainerView implements SeContainer {

    /** Public no-arg constructor used by {@code TestContext} consumers. */
    public ArcSeContainerView() {
    }

    @Override
    public BeanManager getBeanManager() {
        return Arc.container().beanManager();
    }

    @Override
    public boolean isRunning() {
        return Arc.container() != null;
    }

    @Override
    public void close() {
        // No-op: jawelte's CdiTestBeanContainer owns the ArC shutdown
        // in afterAll(). Consumers must not close the container
        // out-of-band via this view.
    }

    @Override
    public Instance<Object> select(Annotation... qualifiers) {
        return delegate().select(qualifiers);
    }

    @Override
    public <U> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
        return delegate().select(subtype, qualifiers);
    }

    @Override
    public <U> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        return delegate().select(subtype, qualifiers);
    }

    @Override
    public boolean isUnsatisfied() {
        return delegate().isUnsatisfied();
    }

    @Override
    public boolean isAmbiguous() {
        return delegate().isAmbiguous();
    }

    @Override
    public void destroy(Object instance) {
        delegate().destroy(instance);
    }

    @Override
    public Instance.Handle<Object> getHandle() {
        return delegate().getHandle();
    }

    @Override
    public Iterable<? extends Instance.Handle<Object>> handles() {
        return delegate().handles();
    }

    @Override
    public Stream<? extends Instance.Handle<Object>> handlesStream() {
        return delegate().handlesStream();
    }

    @Override
    public Iterator<Object> iterator() {
        return delegate().iterator();
    }

    @Override
    public Object get() {
        return delegate().get();
    }

    @Override
    public Stream<Object> stream() {
        return delegate().stream();
    }

    private Instance<Object> delegate() {
        return Arc.container().beanManager().createInstance();
    }
}
