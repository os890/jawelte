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
package org.os890.jawelte.tests.jta.scenario55;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Ticket-006 scenario #55 — multi-PU XA atomicity under Geronimo.
 * Per-scenario META-INF/services override pins
 * GeronimoTransactionManagerProvider + the scenario pom pulls in
 * geronimo-transaction, so it always runs against Geronimo. A
 * {@code @Transactional} method writes into both PUs and Geronimo
 * drives the two-phase commit across both
 * {@code XaDataSourceWrapper}-enlisted {@code XAResource}s.
 */
@EnableTestBeans
public class Scenario55Test {

    @Inject
    private GeronimoCrossPuService service;

    /** No-arg constructor for CDI. */
    public Scenario55Test() {
    }

    @Test
    public void crossPuWritesCommitAtomicallyUnderGeronimo() {
        service.persistIntoBothPus();

        assertThat(service.countInPuA())
                .as("PU 'a' must have one committed row after Geronimo 2PC")
                .isEqualTo(1L);
        assertThat(service.countInPuB())
                .as("PU 'b' must have one committed row after Geronimo 2PC")
                .isEqualTo(1L);
    }
}
