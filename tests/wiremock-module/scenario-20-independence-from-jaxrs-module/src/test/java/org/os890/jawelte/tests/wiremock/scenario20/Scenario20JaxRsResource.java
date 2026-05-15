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
package org.os890.jawelte.tests.wiremock.scenario20;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Minimal JAX-RS resource deployed inside scenario 20's
 * embedded server (booted by jaxrs-module's
 * {@code WireMockLifecycleAdapter}-sibling
 * {@code JaxRsLifecycleAdapter}). Returns a fixed string from
 * {@code GET /scenario-20/jaxrs-ping} so the test can verify
 * the JAX-RS side of the dual-module setup is live.
 */
@ApplicationScoped
@Path("/scenario-20/jaxrs-ping")
public class Scenario20JaxRsResource {

    /** No-arg constructor required by CDI / JAX-RS. */
    public Scenario20JaxRsResource() {
    }

    /**
     * Serve a fixed string so the test can recognise it on the
     * wire.
     *
     * @return the literal string {@code "jaxrs-alive"}
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String ping() {
        return "jaxrs-alive";
    }
}
