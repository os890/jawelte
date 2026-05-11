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
package org.os890.jawelte.tests.jta.scenario38;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Multi-PU writes inside one JTA transaction commit atomically.
 * After the {@code @Transactional} method returns, both per-PU
 * tables contain their respective row — the JTA TM drove a
 * two-phase commit across both XA-enlisted resources.
 */
@EnableTestBeans
public class Scenario38Test {

    @Inject
    private CrossPuService service;

    /** No-arg constructor for CDI. */
    public Scenario38Test() {
    }

    @Test
    public void crossPuWritesCommitAtomically() {
        service.persistIntoBothPus();

        assertThat(service.countInPuA())
                .as("PU 'a' must have one committed row")
                .isEqualTo(1L);
        assertThat(service.countInPuB())
                .as("PU 'b' must have one committed row")
                .isEqualTo(1L);
    }
}
