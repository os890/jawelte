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
package org.os890.jawelte.tests.wiremock.scenario23;

import jakarta.inject.Inject;

import org.os890.jawelte.module.wiremock.api.EnableWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Shared-setup base class annotated with
 * {@link EnableWireMock @EnableWireMock}. The subclass
 * {@link Scenario23Test} does <b>not</b> redeclare the
 * annotation — it picks it up by inheritance because
 * {@code @EnableWireMock} is {@code @Inherited}. The injected
 * {@link WireMockServer} field is likewise inherited.
 *
 * <p>The class name deliberately ends in {@code Base} (not
 * {@code Test}) so Surefire's default filename pattern skips
 * it; only the subclass is treated as a runnable test class.
 */
@EnableWireMock
public class Scenario23Base {

    @Inject
    private WireMockServer server;

    /** No-arg constructor required by the CDI runtime. */
    public Scenario23Base() {
    }

    /**
     * Expose the inherited injection point to the subclass test
     * method. A protected getter is enough; we avoid making the
     * field {@code protected} directly because the project's
     * Checkstyle config rejects raw-field exposure.
     *
     * @return the {@link WireMockServer} injected into this
     *         base instance
     */
    protected WireMockServer server() {
        return server;
    }
}
