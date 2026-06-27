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
package org.os890.jawelte.tests.core.scenario24;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * A custom {@code ServicePriorityResolver} installed via MP Config must
 * govern lifecycle-port ordering too — the contract
 * {@code ServicePriorityResolver}'s javadoc promises ("every other SPI
 * selection then automatically follows the new rule").
 *
 * <p>{@link TestScenarioReversePriorityResolver} orders by
 * {@code @Priority} DESCENDING, so {@code beforeEach} must run the
 * {@code @Priority(200)} port before the {@code @Priority(50)} port —
 * the reverse of the default ascending order. On the unfixed code,
 * lifecycle ports used a hard-coded ascending comparator that ignored
 * the installed resolver, so the order would be {@code 50} then
 * {@code 200}.
 */
class Scenario24Test {

    @Test
    void customResolverGovernsLifecyclePortOrdering() {
        RecordedEvents.ENTRIES.clear();

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario24Subject.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));

        assertThat(RecordedEvents.ENTRIES).containsExactly(
                "port200.beforeEach",
                "port050.beforeEach");
    }
}
