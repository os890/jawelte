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
package org.os890.jawelte.tests.jaxrs.scenario07;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Resource backing {@code GET /order} returning a literal JSON
 * body. The response is consumed by {@code ResponseDiff.forJson}
 * in scenario 07.
 */
@ApplicationScoped
@Path("/order")
public class Scenario07OrderResource {

    /** Default no-arg constructor (CDI-discoverable). */
    public Scenario07OrderResource() {
    }

    /**
     * Returns a literal JSON document as
     * {@code application/json}.
     *
     * @return the literal JSON body
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String getOrder() {
        return "{\"id\":1,\"name\":\"Widget\"}";
    }
}
