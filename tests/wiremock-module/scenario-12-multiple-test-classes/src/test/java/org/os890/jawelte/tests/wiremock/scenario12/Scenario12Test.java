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
package org.os890.jawelte.tests.wiremock.scenario12;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Scenario 12 — multiple test classes get independent
 * {@code WireMockServer} instances. Runs {@link Scenario12SubjectA}
 * and {@link Scenario12SubjectB} sequentially via
 * {@code EngineTestKit}; each subject records its OS-assigned
 * server port into the shared {@link Scenario12PortRecorder}.
 * Asserts the two recorded ports differ — confirming each
 * {@code @EnableWireMock} class boots its own per-class server,
 * not a shared one.
 */
class Scenario12Test {

    @Test
    void twoTestClassesGetIndependentServers() {
        Scenario12PortRecorder.PORT_A.set(0);
        Scenario12PortRecorder.PORT_B.set(0);

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario12SubjectA.class))
                .execute();
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario12SubjectB.class))
                .execute();

        int portA = Scenario12PortRecorder.PORT_A.get();
        int portB = Scenario12PortRecorder.PORT_B.get();

        assertThat(portA)
                .as("subject A recorded a strictly positive port")
                .isGreaterThan(0);
        assertThat(portB)
                .as("subject B recorded a strictly positive port")
                .isGreaterThan(0);
        assertThat(portA)
                .as("subject A and subject B booted independent WireMockServer instances on different ports")
                .isNotEqualTo(portB);
    }
}
