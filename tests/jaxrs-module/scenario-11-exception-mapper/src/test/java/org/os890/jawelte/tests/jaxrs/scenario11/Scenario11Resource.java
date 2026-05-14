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
package org.os890.jawelte.tests.jaxrs.scenario11;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * Resource backing {@code GET /brew} — always throws
 * {@link Scenario11TeapotException}, which the mapper turns into
 * HTTP 418.
 */
@ApplicationScoped
@Path("/brew")
public class Scenario11Resource {

    /** Default no-arg constructor (CDI-discoverable). */
    public Scenario11Resource() {
    }

    /**
     * Unconditionally throws {@link Scenario11TeapotException}.
     *
     * @return never returns normally
     */
    @GET
    public String brew() {
        throw new Scenario11TeapotException();
    }
}
