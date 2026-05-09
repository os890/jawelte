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
package org.os890.jawelte.tests.jpa.scenario35;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Calling {@code UserTransaction.begin()} inside an active
 * {@code @Transactional} method composes like nested {@code @Transactional}:
 * a new EntityManager frame is pushed on {@code TransactionScopedEmHolder};
 * inner persist + UT.commit lands its row independently of the outer tx.
 * Outer's @Transactional commit then lands the outer row. Both visible.
 */
@EnableTestBeans
public class Scenario35Test {

    @Inject
    private MarkerService markerService;

    /** No-arg constructor for CDI. */
    public Scenario35Test() {
    }

    /** Outer @Transactional + inner UT both commit → 2 rows. */
    @Test
    public void userTransactionInsideTransactionalCommitsBoth() throws Exception {
        markerService.outerTransactionalWithInnerUserTransaction("outer", "inner-via-ut");

        assertThat(markerService.countMarkers())
                .as("outer @Transactional row + inner UT row both reach the DB")
                .isEqualTo(2L);
    }
}
