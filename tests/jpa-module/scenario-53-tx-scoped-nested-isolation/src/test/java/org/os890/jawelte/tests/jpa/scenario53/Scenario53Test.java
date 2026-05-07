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
package org.os890.jawelte.tests.jpa.scenario53;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Nested-tx isolation: every tx scope owns a distinct
 * {@code @TransactionScoped} contextual instance. The outer
 * instance must survive the nested call (same id sampled before
 * and after) while the inner tx sees its own freshly-created
 * instance — proving the tx scopes are stacked, not shared.
 */
@EnableTestBeans
public class Scenario53Test {

    @Inject
    private NestedOuterService outerService;

    /** No-arg constructor for CDI. */
    public Scenario53Test() {
    }

    /** Outer's instance survives nested call; inner gets its own. */
    @Test
    public void nestedTxScopeYieldsDistinctInstances() {
        NestedTxScopedTracker.reset();

        NestedTxResult result = outerService.outerThenInner();

        assertThat(result.outerAfter())
                .as("outer's @TransactionScoped instance must survive across the nested call")
                .isEqualTo(result.outerBefore());
        assertThat(result.innerId())
                .as("inner @Transactional opens a new tx scope, so its tracker is a fresh instance")
                .isNotEqualTo(result.outerBefore());

        assertThat(NestedTxScopedTracker.POST_CONSTRUCT_COUNT)
                .as("two tracker instances are created (outer + inner)")
                .hasValue(2);
        assertThat(NestedTxScopedTracker.PRE_DESTROY_COUNT)
                .as("both instances are destroyed when their tx ended")
                .hasValue(2);
    }
}
