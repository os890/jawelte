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
package org.os890.jawelte.tests.jaxrs.scenario20;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * In-memory item resource exercising the full CRUD set on a single
 * resource: list, create, read-one, update, delete. The store is a
 * plain {@link ConcurrentHashMap} held in this
 * {@code @ApplicationScoped} bean — no JPA, no DB, no transactions.
 * Responses are hand-built JSON strings so the scenario doesn't pull
 * in any JSON-B provider.
 */
@Path("/items")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class Scenario20ItemResource {

    private final ConcurrentMap<Long, String> nameById = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong();

    /** Default constructor for CDI. */
    public Scenario20ItemResource() {
    }

    /**
     * @return JSON array of {@code {id,name}} pairs sorted by id (count
     *         in the order the client used PUTs/POSTs is irrelevant —
     *         the round-trip test compares against a deterministic
     *         expected snapshot)
     */
    @GET
    public String list() {
        StringBuilder out = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<Long, String> e : new java.util.TreeMap<>(nameById).entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append("{\"id\":").append(e.getKey())
                    .append(",\"name\":\"").append(escape(e.getValue())).append("\"}");
        }
        return out.append(']').toString();
    }

    /** @param id  identifier supplied by the client
     *  @return JSON {@code {id,name}} for the item with that id, or
     *         404 if no such item is stored */
    @GET
    @Path("/{id}")
    public Response readOne(@PathParam("id") Long id) {
        String name = nameById.get(id);
        if (name == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(
                "{\"id\":" + id + ",\"name\":\"" + escape(name) + "\"}")
                .build();
    }

    /** @param name  the item name (query parameter)
     *  @return JSON {@code {id,name}} for the new item, with the
     *         server-allocated id */
    @POST
    public String create(@QueryParam("name") String name) {
        long id = nextId.incrementAndGet();
        nameById.put(id, name);
        return "{\"id\":" + id + ",\"name\":\"" + escape(name) + "\"}";
    }

    /** @param id    identifier of the item to mutate
     *  @param name  the new name
     *  @return JSON {@code {id,name}} for the updated item */
    @PUT
    @Path("/{id}")
    public String update(@PathParam("id") Long id,
                         @QueryParam("name") String name) {
        nameById.put(id, name);
        return "{\"id\":" + id + ",\"name\":\"" + escape(name) + "\"}";
    }

    /** @param id  identifier of the item to drop
     *  @return JSON {@code {"deletedId": <id>}} */
    @DELETE
    @Path("/{id}")
    public String delete(@PathParam("id") Long id) {
        nameById.remove(id);
        return "{\"deletedId\":" + id + "}";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
