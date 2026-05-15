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
package org.os890.jawelte.tests.wiremock.scenario10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.os890.jawelte.module.wiremock.api.event.WireMockServersStopped;

/**
 * Scenario 10 — verifies the lifecycle adapter stops every
 * registered {@code WireMockServer} in {@code afterAll}. Uses
 * the {@link WireMockServersStopped} CDI event the adapter
 * fires after the last {@code server.stop()} call as a
 * deterministic signal — avoids the TCP-probe timing race
 * (server stop returns before the OS releases the listening
 * socket on some kernels).
 *
 * <p>{@link Scenario10Subject} boots a default
 * {@code WireMockServer}; {@link Scenario10StopRecorder} (an
 * {@code @ApplicationScoped} bean) observes the event and
 * bumps a static counter. After the engine returns, the
 * counter must be {@code 1} — exactly one stop-event was
 * published.
 */
class Scenario10Test {

    @Test
    void wireMockServersStoppedEventIsFiredInAfterAll() {
        Scenario10StopRecorder.FIRED_COUNT.set(0);

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario10Subject.class))
                .execute();

        assertThat(Scenario10StopRecorder.FIRED_COUNT.get())
                .as("WireMockServersStopped fired exactly once after the subject's afterAll")
                .isEqualTo(1);
    }
}
