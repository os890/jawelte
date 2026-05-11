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
package org.os890.jawelte.tests.jta.scenario51;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Ticket-006 scenario #51 — multi-PU XA atomicity under Atomikos.
 * A {@code @Transactional} method writes one row into each of two
 * PUs; Atomikos's TM drives two-phase commit across both
 * XA-enlisted {@code XaDataSourceWrapper}s, and both rows are
 * visible after the method returns.
 */
@EnableTestBeans
public class Scenario51Test {

    @Inject
    private AtomikosCrossPuService service;

    /** No-arg constructor for CDI. */
    public Scenario51Test() {
    }

    @Test
    public void crossPuWritesCommitAtomicallyUnderAtomikos() {
        service.persistIntoBothPus();

        assertThat(service.countInPuA())
                .as("PU 'a' must have one committed row after Atomikos 2PC")
                .isEqualTo(1L);
        assertThat(service.countInPuB())
                .as("PU 'b' must have one committed row after Atomikos 2PC")
                .isEqualTo(1L);
    }
}
