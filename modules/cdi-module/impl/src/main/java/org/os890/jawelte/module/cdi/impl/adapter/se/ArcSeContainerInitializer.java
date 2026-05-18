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
import java.util.Map;
import java.util.stream.Stream;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.util.TypeLiteral;

import org.os890.jawelte.module.cdi.impl.adapter.container.CdiTestBeanContainer;

import io.quarkus.arc.Arc;

/**
 * Minimal {@link SeContainerInitializer} that bootstraps Quarkus ArC
 * at test time. Discovered via {@code ServiceLoader} from
 * {@code META-INF/services/jakarta.enterprise.inject.se.SeContainerInitializer},
 * so the Jakarta SE bootstrap call
 * {@code SeContainerInitializer.newInstance().initialize()} succeeds
 * under the ArC-only POC: the standard Jakarta lookup ordinarily fails
 * with "No valid CDI implementation found" because ArC ships no SE
 * provider.
 *
 * <p>The configuration setters on {@link SeContainerInitializer} are
 * not honored — every {@code addX} / {@code enableX} / {@code setX}
 * method is a no-op that returns {@code this}. {@code initialize()}
 * delegates to {@link CdiTestBeanContainer#bootArcForSeShim()}, which
 * scans the current project's {@code target/classes} and
 * {@code target/test-classes} for bean candidates and runs the same
 * {@code BeanProcessor} pipeline used by the test-driven bootstrap.
 */
public class ArcSeContainerInitializer extends SeContainerInitializer {

    /** Public no-arg constructor required by {@code ServiceLoader}. */
    public ArcSeContainerInitializer() {
    }

    @Override
    public SeContainer initialize() {
        CdiTestBeanContainer.bootArcForSeShim();
        return new ArcSeContainer();
    }

    @Override
    public SeContainerInitializer addBeanClasses(Class<?>... classes) {
        return this;
    }

    @Override
    public SeContainerInitializer addPackages(Class<?>... packageClasses) {
        return this;
    }

    @Override
    public SeContainerInitializer addPackages(boolean scanRecursively, Class<?>... packageClasses) {
        return this;
    }

    @Override
    public SeContainerInitializer addPackages(Package... packages) {
        return this;
    }

    @Override
    public SeContainerInitializer addPackages(boolean scanRecursively, Package... packages) {
        return this;
    }

    @Override
    public SeContainerInitializer addExtensions(Extension... extensions) {
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SeContainerInitializer addExtensions(Class<? extends Extension>... extensions) {
        return this;
    }

    @Override
    public SeContainerInitializer enableInterceptors(Class<?>... interceptorClasses) {
        return this;
    }

    @Override
    public SeContainerInitializer enableDecorators(Class<?>... decoratorClasses) {
        return this;
    }

    @Override
    public SeContainerInitializer selectAlternatives(Class<?>... alternativeClasses) {
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SeContainerInitializer selectAlternativeStereotypes(
            Class<? extends Annotation>... alternativeStereotypeClasses) {
        return this;
    }

    @Override
    public SeContainerInitializer addProperty(String key, Object value) {
        return this;
    }

    @Override
    public SeContainerInitializer setProperties(Map<String, Object> properties) {
        return this;
    }

    @Override
    public SeContainerInitializer disableDiscovery() {
        return this;
    }

    @Override
    public SeContainerInitializer setClassLoader(ClassLoader classLoader) {
        return this;
    }

    /**
     * Minimal {@link SeContainer} backed by Quarkus ArC. All
     * {@code Instance<Object>} lookups delegate to
     * {@code Arc.container().beanManager().createInstance()};
     * {@link #close()} shuts ArC down so a follow-up
     * {@code @AfterAll} on the test class returns the JVM to a clean
     * state.
     */
    private static class ArcSeContainer implements SeContainer {

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
            Arc.shutdown();
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
}
