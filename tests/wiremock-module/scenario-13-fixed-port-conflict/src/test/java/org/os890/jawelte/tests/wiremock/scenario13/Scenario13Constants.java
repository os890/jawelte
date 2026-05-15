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
package org.os890.jawelte.tests.wiremock.scenario13;

/**
 * Compile-time constant shared between the qualifier annotation
 * ({@link SquattedApi}) and the test setup
 * ({@link Scenario13Test}). The qualifier needs the port as a
 * literal expression in {@code @WireMockEndpoint(port=...)}, so
 * a {@code public static final int} declared here is the only
 * mechanism that lets the same value drive both the
 * pre-binding {@code ServerSocket} and the WireMock target
 * port.
 */
public class Scenario13Constants {

    /**
     * Fixed port used by both the squatter {@code ServerSocket}
     * and the {@link SquattedApi @WireMockEndpoint(port=...)}.
     * Chosen well above the IANA registered range and below the
     * Linux ephemeral range (commonly {@code 32768}+) to
     * minimise collision with anything a developer might have
     * already bound.
     */
    public static final int SQUATTED_PORT = 51777;

    /** No-arg constructor — utility holder. */
    protected Scenario13Constants() {
    }
}
