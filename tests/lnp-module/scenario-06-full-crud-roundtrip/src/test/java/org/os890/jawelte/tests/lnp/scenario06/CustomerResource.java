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
package org.os890.jawelte.tests.lnp.scenario06;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.os890.jawelte.tests.lnp.scenario06.entity.Customer;

/**
 * JAX-RS resource exposing the five CRUD verbs on
 * {@link Customer}: list, read-one, create, update-email, delete.
 * Each endpoint is {@code @Transactional} so the mutation commits
 * inside the server-side request scope. Payloads are built via
 * JSON-P and returned as Strings (no JSON-B provider needed).
 */
@Path("/customers")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class CustomerResource {

    @Inject
    private EntityManager em;

    /** Default constructor for CDI. */
    public CustomerResource() {
    }

    /**
     * @return JSON array of all {@link Customer} rows in
     *         ascending-id order
     */
    @GET
    @Transactional
    public String list() {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        em.createQuery("SELECT c FROM Customer c ORDER BY c.id", Customer.class)
                .getResultList()
                .forEach(c -> arr.add(Json.createObjectBuilder()
                        .add("id", c.getId())
                        .add("name", c.getName())
                        .add("email", c.getEmail())));
        return arr.build().toString();
    }

    /**
     * @param id the primary key
     * @return JSON of the matching {@link Customer}; HTTP 404 if no
     *         such row exists
     */
    @GET
    @Path("/{id}")
    @Transactional
    public String readOne(@PathParam("id") Long id) {
        Customer c = em.find(Customer.class, id);
        if (c == null) {
            throw new NotFoundException();
        }
        return Json.createObjectBuilder()
                .add("id", c.getId())
                .add("name", c.getName())
                .add("email", c.getEmail())
                .build().toString();
    }

    /**
     * @param name  the new customer's name (query parameter)
     * @param email the new customer's email (query parameter)
     * @return JSON of the inserted row with the server-assigned id
     */
    @POST
    @Transactional
    public String create(@QueryParam("name") String name,
                         @QueryParam("email") String email) {
        Customer c = new Customer();
        c.setName(name);
        c.setEmail(email);
        em.persist(c);
        em.flush();
        return Json.createObjectBuilder()
                .add("id", c.getId())
                .add("name", c.getName())
                .add("email", c.getEmail())
                .build().toString();
    }

    /**
     * @param id the primary key
     * @param email the new email
     * @return JSON of the updated row
     */
    @PUT
    @Path("/{id}/email")
    @Transactional
    public String updateEmail(@PathParam("id") Long id,
                              @QueryParam("value") String email) {
        Customer c = em.find(Customer.class, id);
        c.setEmail(email);
        em.flush();
        return Json.createObjectBuilder()
                .add("id", c.getId())
                .add("name", c.getName())
                .add("email", c.getEmail())
                .build().toString();
    }

    /**
     * @param id the primary key
     * @return JSON of the form {@code {"deletedId": <id>}}
     */
    @DELETE
    @Path("/{id}")
    @Transactional
    public String delete(@PathParam("id") Long id) {
        Customer c = em.find(Customer.class, id);
        if (c != null) {
            em.remove(c);
            em.flush();
        }
        return Json.createObjectBuilder()
                .add("deletedId", id)
                .build().toString();
    }
}
