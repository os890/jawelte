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
package org.os890.jawelte.tests.jaxrs.scenario19;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Trivial resource so the embedded server has something to
 * register — scenario 19 doesn't hit this endpoint; it asserts
 * that a request to a <em>different</em> path that nobody
 * handles produces a {@code 404}.
 */
@ApplicationScoped
@Path("/hello")
public class Scenario19HelloResource {

    /** Default no-arg constructor (CDI-discoverable). */
    public Scenario19HelloResource() {
    }

    /** @return the literal {@code "hello"} */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "hello";
    }
}
