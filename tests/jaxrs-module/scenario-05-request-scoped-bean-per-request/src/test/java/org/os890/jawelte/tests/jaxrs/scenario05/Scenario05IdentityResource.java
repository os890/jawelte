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
package org.os890.jawelte.tests.jaxrs.scenario05;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Resource backing {@code GET /identity}. Returns the
 * {@code System.identityHashCode} of the injected
 * {@link Scenario05PerRequestBean} for the current request, so two
 * requests within one test method can be compared for instance
 * identity.
 */
@ApplicationScoped
@Path("/identity")
public class Scenario05IdentityResource {

    @Inject
    private Scenario05PerRequestBean bean;

    /** Default no-arg constructor (CDI-discoverable). */
    public Scenario05IdentityResource() {
    }

    /**
     * Report the identity hash of the
     * {@link Scenario05PerRequestBean} that the
     * {@code @RequestScoped} context selected for this HTTP
     * request.
     *
     * @return the identity hash as a decimal string
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String identity() {
        return Integer.toString(bean.identity());
    }
}
