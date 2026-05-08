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
package org.os890.jawelte.tests.jpa.scenario19;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Each nested {@code @Transactional} pushes a fresh tx-scope frame. Outer +
 * inner therefore each receive their own {@code @TransactionScoped} contextual
 * instance — two {@code @PostConstruct} fires, two {@code @PreDestroy} fires
 * across one outer call. Scenario 53 verifies the identity-distinctness angle;
 * this scenario locks in the lifecycle-count angle.
 */
@EnableTestBeans
public class Scenario19Test {

    @Inject
    private NestedOuterService outerService;

    /** No-arg constructor for CDI. */
    public Scenario19Test() {
    }

    /** Outer + inner each contribute one full lifecycle pair. */
    @Test
    public void nestedTxYieldsTwoLifecycles() {
        NestedTracker.reset();

        outerService.outerTouchThenInnerTx();

        assertThat(NestedTracker.POST_CONSTRUCT_COUNT)
                .as("outer creates its own tracker; inner creates a separate one — 2 PostConstructs")
                .hasValue(2);
        assertThat(NestedTracker.PRE_DESTROY_COUNT)
                .as("inner's tracker is destroyed when inner commits; outer's when outer commits")
                .hasValue(2);
    }
}
