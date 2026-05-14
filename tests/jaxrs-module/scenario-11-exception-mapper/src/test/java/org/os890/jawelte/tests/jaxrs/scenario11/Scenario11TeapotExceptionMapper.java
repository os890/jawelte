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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps {@link Scenario11TeapotException} to HTTP {@code 418
 * I'm a teapot} with a literal text body. {@code @ApplicationScoped}
 * so the mapper is a CDI bean — jaxrs-module's
 * lifecycle adapter then resolves the singleton instance via
 * {@code CDI.current().select(...).get()} and registers it on the
 * JAX-RS {@code Application}.
 */
@Provider
@ApplicationScoped
public class Scenario11TeapotExceptionMapper implements ExceptionMapper<Scenario11TeapotException> {

    /** Default no-arg constructor (CDI-discoverable). */
    public Scenario11TeapotExceptionMapper() {
    }

    @Override
    public Response toResponse(Scenario11TeapotException exception) {
        return Response.status(418)
                .type(MediaType.TEXT_PLAIN)
                .entity("teapot")
                .build();
    }
}
