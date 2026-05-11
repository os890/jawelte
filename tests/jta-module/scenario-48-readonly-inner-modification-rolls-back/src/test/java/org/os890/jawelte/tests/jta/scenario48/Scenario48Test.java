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
package org.os890.jawelte.tests.jta.scenario48;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Nested {@code @Transactional} under JTA where the inner level
 * attempts to write inside a {@code REQUIRES_NEW} {@code @ReadOnly}
 * transaction. The inner persist is rolled back; the outer's persist
 * commits. Verifies that exactly one row ends up in the database
 * after both levels complete — the outer's, not the inner's.
 */
@EnableTestBeans
public class Scenario48Test {

    @Inject
    private OuterWriter outerWriter;

    /** No-arg constructor for CDI. */
    public Scenario48Test() {
    }

    @Test
    public void readOnlyInnerRollsBackOuterCommits() {
        outerWriter.persistThenInnerReadOnlyPersist();

        long persisted = outerWriter.countCommittedItems();

        assertThat(persisted)
                .as("outer write must commit (1 row); inner @ReadOnly REQUIRES_NEW write must roll back (0 rows)")
                .isEqualTo(1L);
    }
}
