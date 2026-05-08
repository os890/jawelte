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
package org.os890.jawelte.tests.jpa.scenario38;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * jpa-module fires {@code TransactionStarted} → {@code TransactionBeforeCompletion}
 * → {@code TransactionCommitted} for a successful {@code @Transactional} commit.
 * Each event carries the active persistence-unit name.
 */
@EnableTestBeans
public class Scenario38Test {

    @Inject
    private CommittingService committingService;

    @Inject
    private CommitEventRecorder commitEventRecorder;

    /** No-arg constructor for CDI. */
    public Scenario38Test() {
    }

    /** A successful commit fires Started → BeforeCompletion → Committed in order. */
    @Test
    public void commitFiresThreeEventsInOrderWithPuName() {
        commitEventRecorder.reset();

        committingService.persistAndCommit();

        assertThat(commitEventRecorder.events())
                .as("commit must fire TransactionStarted → TransactionBeforeCompletion "
                        + "→ TransactionCommitted, each carrying the testPU38 name")
                .containsExactly(
                        "started:testPU38",
                        "before:testPU38",
                        "committed:testPU38");
    }
}
