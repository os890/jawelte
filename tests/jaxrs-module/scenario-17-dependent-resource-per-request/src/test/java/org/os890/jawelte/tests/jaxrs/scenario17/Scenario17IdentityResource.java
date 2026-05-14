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
package org.os890.jawelte.tests.jaxrs.scenario17;

import jakarta.enterprise.context.Dependent;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * {@code @Dependent}-scoped resource — registered as a JAX-RS
 * Class (not as a CDI-resolved singleton) by
 * {@code JaxRsLifecycleAdapter}'s {@code @Dependent} routing, so
 * the JAX-RS runtime instantiates a fresh instance per request
 * (the default resource-instance lifecycle of the JAX-RS spec).
 *
 * <p>Exposes its own {@code System.identityHashCode} so the test
 * can compare two responses and verify they came from different
 * instances. No {@code @Inject} fields — registered-as-class
 * resources don't get CDI injection in this scenario.
 */
@Dependent
@Path("/identity")
public class Scenario17IdentityResource {

    /** Default no-arg constructor (instantiated by JAX-RS). */
    public Scenario17IdentityResource() {
    }

    /**
     * @return the identity hash of {@code this} resource instance
     *         as a decimal string
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String identity() {
        return Integer.toString(System.identityHashCode(this));
    }
}
