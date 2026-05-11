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
package org.os890.jawelte.tests.jta.scenario47;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Nested {@code @Transactional} under JTA: a writable outer method
 * calls a {@code REQUIRES_NEW} {@code @ReadOnly} inner method that
 * does not modify. Verifies that the outer's write commits — the
 * inner's read-only marking applies to its own suspended-and-resumed
 * JTA transaction, not to the outer's.
 *
 * <p>Exercises {@code JtaTransactionStrategy}'s suspend / resume
 * deque and the per-JTA-{@code Transaction} {@code EntityManager}
 * keying provided by the {@code @TransactionScoped} EM bean.
 */
@EnableTestBeans
public class Scenario47Test {

    @Inject
    private OuterWriter outerWriter;

    /** No-arg constructor for CDI. */
    public Scenario47Test() {
    }

    @Test
    public void outerWriteSurvivesReadOnlyInner() {
        long innerObserved = outerWriter.persistThenInnerRead();

        long persisted = outerWriter.countCommittedItems();

        assertThat(persisted)
                .as("outer transaction's persist must commit regardless of the inner @ReadOnly tx")
                .isEqualTo(1L);
        assertThat(innerObserved)
                .as("inner @ReadOnly tx ran in its own JTA tx — outer's uncommitted insert is not visible to it")
                .isZero();
    }
}
