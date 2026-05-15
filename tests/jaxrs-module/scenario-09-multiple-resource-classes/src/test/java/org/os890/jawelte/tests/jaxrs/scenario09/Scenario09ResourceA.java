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
package org.os890.jawelte.tests.jaxrs.scenario09;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Resource A — replies with the literal {@code "A"}. */
@ApplicationScoped
@Path("/a")
public class Scenario09ResourceA {

    /** Default no-arg constructor (CDI-discoverable). */
    public Scenario09ResourceA() {
    }

    /**
     * Returns the literal {@code "A"} so the test can distinguish
     * this resource's responses from {@link Scenario09ResourceB}'s.
     *
     * @return the literal {@code "A"}
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String body() {
        return "A";
    }
}
