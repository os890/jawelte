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
package org.os890.jawelte.module.jaxrs.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Activates jawelte's embedded Jakarta REST 4.0 server support for a
 * test class. The annotation is meta-annotated with
 * {@link EnableTestBeans} so applying {@code @EnableJaxRs} to a
 * test class is enough to activate jawelte's CDI machinery as well —
 * users don't need to write both annotations side-by-side. JUnit
 * Jupiter walks the annotation's meta-annotations to discover the
 * {@code @ExtendWith(EnableTestBeans.Proxy.class)} that
 * {@code @EnableTestBeans} carries, registering jawelte's proxy
 * extension automatically. The lifecycle adapter chain (driven by
 * that proxy) then runs as usual; {@code JaxRsLifecycleAdapter}
 * boots {@code SeBootstrap} once it sees {@code @EnableJaxRs} on
 * the test class.
 *
 * <p>jaxrs-module's {@code JaxRsLifecycleAdapter}
 * ({@code @Priority(75)}, between testcontrol at 50 and scope at 100)
 * reads this annotation off the test class in {@code beforeAll},
 * boots {@code SeBootstrap.start} on an OS-assigned local port,
 * and registers the {@link #restResources()} classes as the active
 * REST resources. The {@code TestUrl} bean (also from this api
 * package) is populated with the resolved base URL so test methods
 * can issue HTTP calls against the running server.
 *
 * <p><b>Hex-arch note.</b> No JUnit Jupiter type appears on this
 * api's surface — the JUnit bridge lives entirely in {@code core/api}
 * via {@link EnableTestBeans} (which jaxrs-module/api references
 * only as a Java meta-annotation reference, not by depending on
 * JUnit). All other JUnit interaction happens in {@code core/impl}
 * and in module impl classes that route through {@code TestContext}.
 *
 * <p>{@code @EnableJaxRs} is {@code TYPE}-only. Placing it on a
 * method or any other element is rejected by the compiler.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@EnableTestBeans
public @interface EnableJaxRs {

    /**
     * The JAX-RS resource classes deployed in the embedded server.
     *
     * <p>Each class must be discoverable as a CDI bean — either
     * picked up by {@code beans.xml} with
     * {@code bean-discovery-mode="annotated"} plus a bean-defining
     * annotation ({@code @ApplicationScoped},
     * {@code @RequestScoped}, {@code @Dependent}, etc.) or
     * explicitly contributed via the test class's
     * {@code @TestBean(...)} declarations. jaxrs-module does
     * <em>not</em> auto-allowlist the resource classes; under
     * cdi-module's whitelist mode ({@code limitToTestBeans=true}),
     * the user has to include their resource classes via
     * {@code @TestBean} or by leaving whitelist mode off.
     *
     * <p>The resource's CDI scope is whatever CDI assigns — the
     * module does <em>not</em> override it. Resources annotated
     * {@code @ApplicationScoped} are shared across requests;
     * {@code @RequestScoped} resources are recreated per HTTP
     * request (the {@code @RequestScoped} context is activated by
     * jaxrs-module's request filter for each incoming request);
     * {@code @Dependent} resources are recreated per JAX-RS
     * dispatch.
     *
     * <p>The attribute has no default — every {@code @EnableJaxRs}
     * declaration must supply at least one resource class.
     *
     * @return the resource classes to deploy; never {@code null}
     *         (the compiler enforces the absence of a default)
     */
    Class<?>[] restResources();
}
