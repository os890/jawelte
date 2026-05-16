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
package org.os890.jawelte.core.impl.adapter.extension;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;

import org.os890.jawelte.core.api.port.BeanScopeMapperPort;
import org.os890.jawelte.core.api.port.TestContext;

/**
 * Fixed thin CDI Extension wrapper around the active
 * {@link BeanScopeMapperPort}. Hooks into every
 * {@link ProcessAnnotatedType} event the CDI runtime delivers,
 * asks the port whether to remap the bean's scope, and applies
 * the returned {@link BeanScopeMapperPort.ScopeMappingMetadata} through
 * {@code AnnotatedTypeConfigurator}.
 *
 * <p>This extension is <b>not</b> replaceable — it is the single
 * hook into the CDI extension lifecycle. The replaceable piece is
 * the {@code BeanScopeMapperPort} the extension delegates to. The
 * port is resolved once during {@link BeforeBeanDiscovery} via
 * {@link TestContext#loadService(Class)}; customers ship their
 * own port impl at higher priority to override the default.
 *
 * <p><b>Lifecycle.</b> {@code BeforeBeanDiscovery} fires once per
 * CDI container boot. The port impl is resolved there because
 * {@code TestContext.loadService} reads MP Config and uses the
 * configured {@code ServicePriorityResolver} — both are fully
 * available by that point. Storing the resolved port avoids
 * re-resolution on every {@code ProcessAnnotatedType} event (one
 * per discovered bean class — potentially hundreds per test).
 *
 * <p><b>Bean-metadata mutation.</b> When the port returns a
 * {@link BeanScopeMapperPort.ScopeMappingMetadata}, the configurator removes
 * every annotation whose type is in
 * {@link BeanScopeMapperPort.ScopeMappingMetadata#annotationsToRemove()} and
 * adds a Proxy-built literal of
 * {@link BeanScopeMapperPort.ScopeMappingMetadata#targetScope()}. The literal
 * cache lives on this extension so building the same scope
 * literal twice (one per scope, not one per bean) costs only one
 * {@code Proxy.newProxyInstance} call.
 *
 * <p>Loaded by the CDI runtime via
 * {@code META-INF/services/jakarta.enterprise.inject.spi.Extension}
 * shipped in {@code core/impl}.
 */
public class ScopeRemapCdiExtension implements Extension {

    private final ConcurrentMap<Class<? extends Annotation>, Annotation> literalCache =
            new ConcurrentHashMap<>();

    private BeanScopeMapperPort port;

    /** No-arg constructor required by the CDI runtime. */
    public ScopeRemapCdiExtension() {
    }

    void onBeforeBeanDiscovery(@Observes BeforeBeanDiscovery event) {
        this.port = TestContext.loadService(BeanScopeMapperPort.class);
    }

    void onProcessAnnotatedType(@Observes ProcessAnnotatedType<?> event) {
        if (port == null) {
            return;
        }
        Class<?> beanClass = event.getAnnotatedType().getJavaClass();
        port.mapScope(beanClass).ifPresent(mapping -> apply(event, mapping));
    }

    private void apply(ProcessAnnotatedType<?> event, BeanScopeMapperPort.ScopeMappingMetadata mapping) {
        event.configureAnnotatedType()
                .remove(annotation -> mapping.annotationsToRemove().contains(annotation.annotationType()))
                .add(literalFor(mapping.targetScope()));
    }

    private Annotation literalFor(Class<? extends Annotation> scope) {
        return literalCache.computeIfAbsent(scope, ScopeRemapCdiExtension::buildLiteral);
    }

    private static Annotation buildLiteral(Class<? extends Annotation> scope) {
        return (Annotation) Proxy.newProxyInstance(
                scope.getClassLoader(),
                new Class<?>[]{scope},
                (proxy, method, args) -> switch (method.getName()) {
                    case "annotationType" -> scope;
                    case "toString" -> "@" + scope.getName();
                    case "hashCode" -> 0;
                    case "equals" -> args != null
                            && args.length == 1
                            && args[0] instanceof Annotation other
                            && other.annotationType().equals(scope);
                    default -> method.getDefaultValue();
                });
    }
}
