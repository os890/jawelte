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
package org.os890.jawelte.tests.jaxrs.scenario04;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Resource backing {@code GET /greet/{name}}. Delegates the actual
 * greeting composition to an {@code @Inject}-ed
 * {@link Scenario04GreetingService} so the test can verify both
 * that JAX-RS dispatches to the resource AND that CDI satisfied
 * the resource's injection point on the server thread.
 */
@ApplicationScoped
@Path("/greet")
public class Scenario04GreetResource {

    @Inject
    private Scenario04GreetingService service;

    /** Default no-arg constructor (CDI-discoverable). */
    public Scenario04GreetResource() {
    }

    /**
     * Compose a greeting for the path-supplied name.
     *
     * @param name the path segment {@code name}
     * @return {@code "Hello, " + name}, computed by the injected
     *         service
     */
    @GET
    @Path("/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    public String greet(@PathParam("name") String name) {
        return service.greet(name);
    }
}
