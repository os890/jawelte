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
package org.os890.jawelte.tests.batch.scenario01;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.batch.runtime.BatchStatus;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.batch.api.BatchExecution;

/**
 * Scenario 01 — smoke test of the full fire→observe→populate
 * path. A trivial JSL job ({@code META-INF/batch-jobs/simple-job.xml})
 * referencing a {@code @Named("simpleBatchlet") @Dependent} batchlet
 * that returns {@code "COMPLETED"} from {@code process()}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>{@code Event<BatchExecution>.fire(...)} blocks until the
 *       observer's polling loop sees a terminal status.</li>
 *   <li>The event is populated with the final {@code JobExecution}
 *       before {@code fire(...)} unblocks
 *       ({@code getJobExecution()} non-null,
 *       {@code getStatus()} is {@link BatchStatus#COMPLETED}).</li>
 *   <li>The executionId assigned by the {@code JobOperator} is
 *       visible via {@link BatchExecution#getExecutionId()}.</li>
 * </ul>
 */
@EnableTestBeans
class Scenario01Test {

    @Inject
    private Event<BatchExecution> batchEvent;

    @Test
    void simpleJobCompletes() {
        BatchExecution execution = new BatchExecution("simple-job");

        batchEvent.fire(execution);

        assertThat(execution.getStatus())
                .as("terminal BatchStatus after the observer's polling loop")
                .isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getJobExecution())
                .as("JobExecution populated by the observer")
                .isNotNull();
        assertThat(execution.getExecutionId())
                .as("JobOperator-assigned executionId matches the JobExecution snapshot")
                .isEqualTo(execution.getJobExecution().getExecutionId());
    }
}
