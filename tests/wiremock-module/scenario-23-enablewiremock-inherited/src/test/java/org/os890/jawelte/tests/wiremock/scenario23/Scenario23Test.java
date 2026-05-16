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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Scenario 23 — a subclass extending an
 * {@code @EnableWireMock}-annotated base class boots the full
 * lifecycle without re-declaring the annotation itself. Verifies
 * the {@code @Inherited} meta-annotation on {@code @EnableWireMock}:
 * {@link Scenario23Base} carries the activation; this subclass
 * carries only the {@code @Test} method and reads the
 * inherited {@code WireMockServer} via the protected getter.
 */
class Scenario23Test extends Scenario23Base {

    @Test
    void inheritedActivationBootsWireMock() {
        assertThat(server())
                .as("the inherited injection point resolved — @EnableWireMock activated via @Inherited")
                .isNotNull();
        assertThat(server().isRunning())
                .as("the WireMockServer started by the inherited @EnableWireMock is live")
                .isTrue();
        assertThat(server().port())
                .as("OS-assigned port is strictly positive")
                .isGreaterThan(0);
    }
}
