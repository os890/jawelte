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
package org.os890.jawelte.tests.jpa.scenario24;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * One {@code @Transactional} method writes into two persistence units. Both
 * per-PU {@code EntityTransaction}s commit independently when the method
 * returns; both rows are visible from a fresh tx afterwards. Locks in
 * jpa-module's multi-PU lazy-join + flush-all-then-commit-all path
 * ({@code DefaultResourceLocalTransactionStrategy}).
 */
@EnableTestBeans
public class Scenario24Test {

    @Inject
    private CrossPuService crossPuService;

    /** No-arg constructor for CDI. */
    public Scenario24Test() {
    }

    /** Persisting into both PUs from one tx leaves one row in each. */
    @Test
    public void crossPuTransactionalPersistsBothRows() {
        crossPuService.persistIntoBothPus();

        assertThat(crossPuService.countInPuA())
                .as("PU 'a' must have one row after the cross-PU @Transactional commits")
                .isEqualTo(1L);
        assertThat(crossPuService.countInPuB())
                .as("PU 'b' must have one row after the cross-PU @Transactional commits")
                .isEqualTo(1L);
    }
}
