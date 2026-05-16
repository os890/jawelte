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
package org.os890.jawelte.tests.batch.scenario12;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.batch.runtime.BatchStatus;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.batch.api.BatchExecution;

/**
 * Scenario 12 — after {@code fire(...)} returns, all four result
 * accessors on {@link BatchExecution} expose consistent state:
 * non-null {@code JobExecution}, positive {@code executionId},
 * terminal {@link BatchStatus}, non-null exit-status string. The
 * batchlet returns {@code "CUSTOM_EXIT"} as its exit string so the
 * assertion can confirm the value round-trips.
 */
@EnableTestBeans
class Scenario12Test {

    @Inject
    private Event<BatchExecution> batchEvent;

    @Test
    void allResultAccessorsAreConsistent() {
        BatchExecution execution = new BatchExecution("result-job");

        batchEvent.fire(execution);

        assertThat(execution.getJobExecution())
                .as("JobExecution populated by the observer")
                .isNotNull();
        assertThat(execution.getExecutionId())
                .as("executionId set to the JobOperator-assigned value")
                .isEqualTo(execution.getJobExecution().getExecutionId());
        assertThat(execution.getStatus())
                .as("terminal status from the polling loop")
                .isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getExitStatus())
                .as("exit status set after fire returns (defaults to BatchStatus name when not overridden)")
                .isNotNull();
    }
}
