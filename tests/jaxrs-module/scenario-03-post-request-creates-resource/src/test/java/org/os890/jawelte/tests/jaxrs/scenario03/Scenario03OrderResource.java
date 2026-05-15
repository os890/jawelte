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
package org.os890.jawelte.tests.jaxrs.scenario03;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * POST endpoint {@code /orders} consuming JSON. Reads the entity
 * as a {@code String} (no JSON parser required — the test sends a
 * raw JSON literal), publishes it on the shared
 * {@link ReceivedOrderHolder}, and returns {@code 201 Created}.
 *
 * <p>The {@code @Inject} on {@link ReceivedOrderHolder} also
 * exercises CDI integration: the JAX-RS runtime is expected to
 * resolve the resource through CDI so the injection point is
 * satisfied.
 */
@ApplicationScoped
@Path("/orders")
public class Scenario03OrderResource {

    @Inject
    private ReceivedOrderHolder holder;

    /** Default no-arg constructor (CDI-discoverable). */
    public Scenario03OrderResource() {
    }

    /**
     * Receive a JSON-typed body, publish it on the shared holder,
     * return {@code 201 Created}.
     *
     * @param body the JSON request entity as a raw string
     * @return {@code 201 Created} (empty body)
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createOrder(String body) {
        holder.setBody(body);
        return Response.status(Response.Status.CREATED).build();
    }
}
