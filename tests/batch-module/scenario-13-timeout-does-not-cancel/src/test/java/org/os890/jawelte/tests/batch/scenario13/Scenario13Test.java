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
package org.os890.jawelte.tests.batch.scenario13;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import jakarta.batch.operations.JobOperator;
import jakarta.batch.runtime.BatchStatus;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.batch.api.BatchExecution;

/**
 * Scenario 13 — observer timeout does not cancel the job. The
 * test fires a 2.5 s batchlet with a 500 ms timeout; the observer
 * throws {@link IllegalStateException}. Afterwards we sleep past
 * the batchlet's natural runtime, recover the executionId the
 * batchlet stashed in {@link OrphanBatchlet#CAPTURED_EXECUTION_ID},
 * and query the {@link JobOperator} directly — the orphaned job
 * is observably {@link BatchStatus#COMPLETED}, proving the
 * runtime kept running it on the jBatch thread pool.
 *
 * <p>Tagged {@code "slow"} (4 s total wall time).
 */
@EnableTestBeans
@Tag("slow")
class Scenario13Test {

    @Inject
    private Event<BatchExecution> batchEvent;

    @Inject
    private JobOperator jobOperator;

    @Test
    void timeoutDoesNotCancelOrphanedJob() throws InterruptedException {
        OrphanBatchlet.CAPTURED_EXECUTION_ID.set(-1L);

        BatchExecution execution = new BatchExecution("orphan-job")
                .timeout(Duration.ofMillis(500L));

        assertThatThrownBy(() -> batchEvent.fire(execution))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not complete within");

        Thread.sleep(3_500L);

        long capturedId = OrphanBatchlet.CAPTURED_EXECUTION_ID.get();
        assertThat(capturedId)
                .as("batchlet started and recorded its executionId before timeout fired")
                .isNotEqualTo(-1L);

        assertThat(jobOperator.getJobExecution(capturedId).getBatchStatus())
                .as("orphaned job kept running on the jBatch thread and eventually COMPLETED")
                .isEqualTo(BatchStatus.COMPLETED);
    }
}
