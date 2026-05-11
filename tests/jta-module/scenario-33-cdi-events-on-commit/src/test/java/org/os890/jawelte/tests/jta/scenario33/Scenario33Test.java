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
package org.os890.jawelte.tests.jta.scenario33;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Port of jpa-module scenario 38 — CDI tx events fire on commit
 * under JTA. {@code JtaTransactionStrategy} fires
 * {@code TransactionStarted} on begin, {@code TransactionBeforeCompletion}
 * on commit entry, and {@code TransactionCommitted} on success.
 * Under JTA the events fire <strong>once per JTA transaction</strong>
 * (not once per PU as under RESOURCE_LOCAL) — the
 * {@code persistenceUnitName} field on the payload is empty to
 * signal "transaction-wide".
 */
@EnableTestBeans
public class Scenario33Test {

    @Inject
    private CommittingService committingService;

    @Inject
    private CommitEventRecorder recorder;

    /** No-arg constructor for CDI. */
    public Scenario33Test() {
    }

    @Test
    public void cdiEventsFireOnceOnCommit() {
        int startedBefore = recorder.startedCount();
        int beforeCompletionBefore = recorder.beforeCompletionCount();
        int committedBefore = recorder.committedCount();

        committingService.commitOne();

        assertThat(recorder.startedCount() - startedBefore)
                .as("TransactionStarted must fire exactly once per JTA tx")
                .isEqualTo(1);
        assertThat(recorder.beforeCompletionCount() - beforeCompletionBefore)
                .as("TransactionBeforeCompletion must fire exactly once per JTA tx")
                .isEqualTo(1);
        assertThat(recorder.committedCount() - committedBefore)
                .as("TransactionCommitted must fire exactly once per JTA tx")
                .isEqualTo(1);
    }
}
