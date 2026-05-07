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
package org.os890.jawelte.tests.jpa.scenario55;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Cross-level isolation: outer writable {@code @Transactional}
 * commits its own inserts, the nested
 * {@code @ReadOnly @Transactional} rolls back its own inserts and
 * mutations. Net DB after the call: the seeded note still has its
 * original text, the inner-inserted note is absent, and both
 * outer-inserted notes are present (1 + 2 = 3 rows).
 */
@EnableTestBeans
public class Scenario55Test {

    @Inject
    private OuterWritableService outerWritableService;

    /** No-arg constructor for CDI. */
    public Scenario55Test() {
    }

    /** Outer writes commit; inner @ReadOnly rolls back. */
    @Test
    public void writableOuterCommitsWhileReadOnlyInnerRollsBack() {
        Long seedId = outerWritableService.seed("seeded-original");

        outerWritableService.writeAroundReadOnlyInner(seedId, "INNER-MUTATION-DISCARDED");

        assertThat(outerWritableService.currentText(seedId))
                .as("inner @ReadOnly setter mutation must not reach the database")
                .isEqualTo("seeded-original");
        assertThat(outerWritableService.countNotes())
                .as("seed + 2 outer-writes survive; inner @ReadOnly insert is dropped")
                .isEqualTo(3L);
    }
}
