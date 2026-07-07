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
package org.os890.jawelte.tests.jpa.scenario71;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.persistence.FlushModeType;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Nested {@code @ReadOnly}: an {@code @ReadOnly} method that calls
 * another {@code @ReadOnly} method. Every {@code @Transactional}
 * invocation starts a fresh transaction, so each {@code @ReadOnly}
 * level marks its own frame rollback-only. Both levels' writes must be
 * discarded, and the outer level must remain read-only across the
 * inner level's return.
 */
@EnableTestBeans
public class Scenario71Test {

    @Inject
    private OuterReadOnlyService outerReadOnlyService;

    /** No-arg constructor for CDI. */
    public Scenario71Test() {
    }

    /** Both the outer and the nested @ReadOnly writes roll back. */
    @Test
    public void nestedReadOnlyWritesAreAllDiscarded() {
        outerReadOnlyService.seed("committed-seed");

        outerReadOnlyService.outerAndNestedReadOnlyWrites("outer", "inner");

        assertThat(outerReadOnlyService.countNotes())
                .as("only the committed seed survives; both @ReadOnly levels roll back")
                .isEqualTo(1L);
    }

    /** The outer level stays read-only after the nested call returns. */
    @Test
    public void outerStaysReadOnlyAfterNestedReadOnlyReturns() {
        FlushModeType outerModeAfterNested =
                outerReadOnlyService.outerFlushModeAfterNestedReadOnly("inner");

        assertThat(outerModeAfterNested)
                .as("outer @ReadOnly must still be COMMIT once the nested @ReadOnly has unwound")
                .isEqualTo(FlushModeType.COMMIT);
    }
}
