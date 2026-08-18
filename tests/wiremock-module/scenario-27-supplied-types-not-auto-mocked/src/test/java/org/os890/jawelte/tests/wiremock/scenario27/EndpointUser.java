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
package org.os890.jawelte.tests.wiremock.scenario27;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * An application bean with one plain {@code @Inject WireMock} — the
 * shape that collides with auto-mocking when wiremock-module does not
 * record the types it supplies — qualified, because the unqualified case
 * is satisfied by wiremock-module's own {@code @Produces} producer and
 * never reaches auto-mock at all.
 */
@ApplicationScoped
public class EndpointUser {

    @Inject
    @PaymentApi
    private WireMock client;

    /** No-arg constructor required by CDI. */
    public EndpointUser() {
    }

    /** @return the injected client, for assertions about its origin */
    public WireMock client() {
        return client;
    }
}
