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
package org.os890.jawelte.tests.jaxrs.scenario18;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Plain JAX-RS resource — deliberately carries NO CDI
 * bean-defining annotation (no {@code @ApplicationScoped},
 * {@code @RequestScoped}, {@code @Dependent}, etc.). Under
 * {@code bean-discovery-mode="annotated"} the class is invisible
 * to CDI, so the lifecycle adapter's reflective no-arg fallback
 * is what instantiates it for the JAX-RS server.
 */
@Path("/plain")
public class Scenario18PlainResource {

    /** Default no-arg constructor used by the reflective fallback. */
    public Scenario18PlainResource() {
    }

    /**
     * Returns the literal {@code "plain-hello"} so the test can
     * verify the resource actually serves traffic through the
     * fallback path.
     *
     * @return the literal {@code "plain-hello"}
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "plain-hello";
    }
}
