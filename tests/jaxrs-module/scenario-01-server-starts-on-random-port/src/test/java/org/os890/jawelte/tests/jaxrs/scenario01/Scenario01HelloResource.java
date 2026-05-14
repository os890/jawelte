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
package org.os890.jawelte.tests.jaxrs.scenario01;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Minimal JAX-RS resource used by scenario 01. Single {@code GET
 * /hello} endpoint returning a plain-text body. {@code @ApplicationScoped}
 * so {@code beans.xml} with {@code bean-discovery-mode="annotated"}
 * picks it up as a CDI bean, which is the prerequisite for
 * jaxrs-module to register it through {@code @EnableJaxRs.restResources}.
 */
@ApplicationScoped
@Path("/hello")
public class Scenario01HelloResource {

    /** Default no-arg constructor (CDI-discoverable). */
    public Scenario01HelloResource() {
    }

    /**
     * Returns the literal string {@code "hello"} as
     * {@code text/plain}. Scenario 01 doesn't actually invoke the
     * endpoint (it asserts on the resolved port only) — the
     * endpoint is here to make the resource a non-trivial
     * registration target.
     *
     * @return the literal string {@code "hello"}
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "hello";
    }
}
