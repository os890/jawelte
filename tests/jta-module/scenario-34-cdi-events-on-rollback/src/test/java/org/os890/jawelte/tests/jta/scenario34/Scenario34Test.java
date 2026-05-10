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
package org.os890.jawelte.tests.jta.scenario34;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Port of jpa-module scenario 39 — CDI tx events fire on rollback
 * under JTA. {@code TransactionStarted} +
 * {@code TransactionBeforeCompletion} + {@code TransactionRolledBack}
 * each fire exactly once; {@code TransactionCommitted} does not.
 */
@EnableTestBeans
public class Scenario34Test {

    @Inject
    private RollbackingService rollbackingService;

    @Inject
    private RollbackEventRecorder recorder;

    /** No-arg constructor for CDI. */
    public Scenario34Test() {
    }

    @Test
    public void cdiEventsFireOnceOnRollback() {
        int startedBefore = recorder.startedCount();
        int beforeCompletionBefore = recorder.beforeCompletionCount();
        int committedBefore = recorder.committedCount();
        int rolledBackBefore = recorder.rolledBackCount();

        assertThatThrownBy(rollbackingService::rollbackOne)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("intentional rollback driver");

        assertThat(recorder.startedCount() - startedBefore)
                .as("TransactionStarted must fire exactly once on the rollback path")
                .isEqualTo(1);
        assertThat(recorder.beforeCompletionCount() - beforeCompletionBefore)
                .as("TransactionBeforeCompletion must fire exactly once on the rollback path")
                .isEqualTo(1);
        assertThat(recorder.rolledBackCount() - rolledBackBefore)
                .as("TransactionRolledBack must fire exactly once on the rollback path")
                .isEqualTo(1);
        assertThat(recorder.committedCount() - committedBefore)
                .as("TransactionCommitted must NOT fire on the rollback path")
                .isZero();
    }
}
