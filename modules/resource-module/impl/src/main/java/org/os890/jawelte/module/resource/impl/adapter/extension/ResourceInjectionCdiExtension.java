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
package org.os890.jawelte.module.resource.impl.adapter.extension;

import java.util.List;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessInjectionTarget;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.resource.api.port.ResourceLookup;
import org.os890.jawelte.module.resource.impl.ResourceFields;

/**
 * CDI Extension shipped by resource-module. Makes
 * {@code @Resource(lookup = "...")} work, so an application's
 * production wiring runs in a test without a test-only producer
 * standing in for it.
 *
 * <p><b>How.</b> At {@code ProcessInjectionTarget} the extension looks
 * at the type being processed; if it declares no named
 * {@code @Resource} field it does nothing at all, and the runtime's own
 * injection target is used untouched. Otherwise the target is wrapped
 * so that after the runtime has injected its own fields, the
 * {@code @Resource} ones are filled through the {@link ResourceLookup}
 * port. That is core CDI SPI and behaves the same on OpenWebBeans and
 * Weld.
 *
 * <p><b>Why not a bean.</b> {@code @Resource} fields are not CDI
 * injection points and must not become any: no
 * {@code ProcessInjectionPoint} fires for them, nothing goes through
 * typesafe resolution, and cdi-module's auto-mocking never sees them —
 * so there is no competing synthetic bean and nothing to record through
 * {@code SuppliedTypeRegistry}.
 *
 * <p><b>Scope, today.</b> Fields whose declaration carries a
 * {@code lookup}, {@code mappedName} or {@code name}. A bare
 * {@code @Resource} is left exactly as it was — see
 * {@link ResourceFields}. Setter ({@code @Resource} on a method) and
 * class-level declarations are not handled yet.
 *
 * <p><b>The test class is not covered.</b> cdi-module builds the test
 * instance's {@code InjectionTarget} at runtime through
 * {@code BeanManager.getInjectionTargetFactory(...)}, which is not the
 * discovery-time path this extension observes. A {@code @Resource}
 * field on a test class is therefore left alone. Application beans —
 * what the production wiring actually consists of — are covered, which
 * is the point of the module.
 *
 * <p>Loaded by the CDI runtime via the
 * {@code META-INF/services/jakarta.enterprise.inject.spi.Extension}
 * registration shipped in this module.
 */
public class ResourceInjectionCdiExtension implements Extension {

    // Resolved once per container on the bootstrap thread. Weld
    // dispatches the type-processing events on ForkJoinPool workers
    // whose context ClassLoader does not carry this module's
    // classpath, so an SPI lookup deferred to that point would fail
    // inside TestContext's reflective instantiation.
    private ResourceLookup resourceLookup;

    /** No-arg constructor required by the CDI runtime. */
    public ResourceInjectionCdiExtension() {
    }

    void onBeforeBeanDiscovery(@Observes BeforeBeanDiscovery event) {
        this.resourceLookup = TestContext.loadService(ResourceLookup.class);
    }

    <X> void onProcessInjectionTarget(@Observes ProcessInjectionTarget<X> event) {
        if (resourceLookup == null) {
            // No ResourceLookup on the classpath at all. Nothing this
            // module can do, and nothing it should break.
            return;
        }
        List<ResourceFields.Target> targets = ResourceFields.of(event.getAnnotatedType());
        if (targets.isEmpty()) {
            return;
        }
        event.setInjectionTarget(new ResourceInjectionTarget<>(
                event.getInjectionTarget(),
                targets,
                resourceLookup,
                event.getAnnotatedType().getJavaClass()));
    }
}
