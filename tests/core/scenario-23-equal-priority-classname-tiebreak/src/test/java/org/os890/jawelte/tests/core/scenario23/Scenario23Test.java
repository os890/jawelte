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
package org.os890.jawelte.tests.core.scenario23;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Two lifecycle ports with the SAME {@code @Priority(75)} (the real
 * {@code JaxRsLifecycleAdapter} / {@code WireMockLifecycleAdapter} case)
 * must run in a deterministic order: the priority sort breaks ties by
 * full class name, so {@code …PortAlpha} runs before {@code …PortZulu}
 * regardless of {@code ServiceLoader} enumeration (service-file) order.
 *
 * <p>The service file deliberately lists {@code …PortZulu} first; on a
 * priority-value-only comparator (no tiebreak, stable sort) the order
 * would follow that file order ({@code zulu} then {@code alpha}). This
 * scenario asserts the class-name order instead.
 *
 * <p>This scenario's classpath has no MicroProfile Config impl (it is
 * {@code provided}-scope), so it also pins that lifecycle ordering still
 * works — and still applies the class-name tiebreak — via the built-in
 * fallback used when the swappable resolver cannot be consulted.
 */
class Scenario23Test {

    @Test
    void equalPriorityPortsRunInClassNameTiebreakOrder() {
        RecordedEvents.ENTRIES.clear();

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario23Subject.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));

        assertThat(RecordedEvents.ENTRIES).containsExactly(
                "alpha.beforeEach",
                "zulu.beforeEach");
    }
}
