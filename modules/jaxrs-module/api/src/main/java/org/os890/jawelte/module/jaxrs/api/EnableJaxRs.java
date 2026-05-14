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

/**
 * Activates jawelte's embedded Jakarta REST 4.0 server support for a
 * test class. Must be combined with
 * {@code @EnableTestBeans} on the same class — the JAX-RS module
 * looks up resource beans from the CDI container that
 * {@code @EnableTestBeans} brings up, so the CDI container has to be
 * active by the time the JAX-RS lifecycle adapter runs.
 *
 * <p>jaxrs-module's {@code JaxRsLifecycleAdapter}
 * ({@code @Priority(75)}, between testcontrol at 50 and scope at 100)
 * reads this annotation off the test class in {@code beforeAll},
 * boots {@code SeBootstrap.start} on port {@code 0} (the OS-assigned
 * free port), and registers the {@link #restResources()} classes as
 * the active REST resources. The {@code TestUrl} bean (also from
 * this api package) is populated with the resolved base URL so test
 * methods can issue HTTP calls against the running server.
 *
 * <p>Validation: {@code @EnableJaxRs} without
 * {@code @EnableTestBeans} on the test class fails the lifecycle
 * with
 * {@code IllegalStateException("@EnableJaxRs requires @EnableTestBeans on the test class: {className}")}.
 *
 * <p>{@code @EnableJaxRs} is {@code TYPE}-only. Placing it on a
 * method or any other element is rejected by the compiler.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
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
