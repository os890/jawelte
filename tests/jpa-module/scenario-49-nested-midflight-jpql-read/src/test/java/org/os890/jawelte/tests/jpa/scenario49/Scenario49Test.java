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
package org.os890.jawelte.tests.jpa.scenario49;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Outer {@code @Transactional} calls a separate inner
 * {@code @Transactional} bean that persists + commits one row;
 * after the inner returns, outer issues a JPQL count and sees
 * inner's committed row mid-flight.
 *
 * <p>JPQL counts query the database, not Hibernate's L1 cache, so
 * the read is correct regardless of whether the outer EM is
 * {@code clear()}'d after the inner pop. Documents the supported
 * "outer reads inner's commits" pattern.
 */
@EnableTestBeans
public class Scenario49Test {

    @Inject
    private OuterReaderService outerReaderService;

    /** No-arg constructor for CDI. */
    public Scenario49Test() {
    }

    /** Outer JPQL sees inner's committed row mid-flight. */
    @Test
    public void outerJpqlReadsInnerCommittedRow() {
        long midFlightCount = outerReaderService.readMidFlightAfterInnerCommit("inner-row");
        assertThat(midFlightCount)
                .as("outer JPQL count between inner commit and outer commit should include inner's row")
                .isEqualTo(1L);

        assertThat(outerReaderService.countPeople())
                .as("after outer commits, only inner's row is in the table — outer didn't persist anything")
                .isEqualTo(1L);
    }

    /**
     * Three-level flow with a tail persist. L1-A lands on outer's EM,
     * inner persists + commits L2-B on its own EM frame, outer reads
     * the JPQL count mid-flight, then outer persists L1-C. After the
     * outer commit, all three rows are in the DB. Mirrors POC's
     * {@code NestedTransactionalTest.threeLevelWithMidRead} (Order 10).
     */
    @Test
    public void threeLevelMidFlightCountAndOuterCommitTotal() {
        long midFlightCount = outerReaderService.threeLevelMidFlightThenTailPersist();

        assertThat(midFlightCount)
                .as("mid-flight JPQL count between L2-B's commit and outer's L1-C persist "
                        + "must see at least the inner's already-committed L2-B row")
                .isGreaterThanOrEqualTo(1L);

        assertThat(outerReaderService.countPeople())
                .as("after the outer @Transactional commits, all three rows (L1-A, L2-B, L1-C) "
                        + "must be present in the DB")
                .isEqualTo(3L);
    }
}
