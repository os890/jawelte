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
package org.os890.jawelte.tests.jaxrs.scenario12;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/** Placeholder resource so the {@code restResources} attribute on
 *  the broken subject is non-empty. The lifecycle never reaches
 *  the point of registering it — the validator fires first. */
@ApplicationScoped
@Path("/never-reached")
public class Scenario12HelloResource {

    /** Default no-arg constructor (CDI-discoverable). */
    public Scenario12HelloResource() {
    }

    /** @return the literal {@code "unreached"} */
    @GET
    public String body() {
        return "unreached";
    }
}
