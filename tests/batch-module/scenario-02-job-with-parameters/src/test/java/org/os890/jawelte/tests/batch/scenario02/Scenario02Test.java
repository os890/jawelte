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
package org.os890.jawelte.tests.batch.scenario02;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import jakarta.batch.runtime.BatchStatus;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.batch.api.BatchExecution;

/**
 * Scenario 02 — verifies that {@link BatchExecution#param(String, String)}
 * calls accumulate into the {@link java.util.Properties} bag that the
 * observer hands to {@code JobOperator.start(jobName, parameters)}.
 * The batchlet records what the runtime exposed via
 * {@code JobContext.getProperties()}; the test asserts the values
 * round-trip cleanly.
 */
@EnableTestBeans
class Scenario02Test {

    @Inject
    private Event<BatchExecution> batchEvent;

    @Test
    void parametersReachTheBatchlet() {
        BatchExecution execution = new BatchExecution("param-job")
                .param("inputFile", "data.csv")
                .param("threshold", "42");

        batchEvent.fire(execution);

        assertThat(execution.getStatus())
                .as("job completes when batchlet returns COMPLETED")
                .isEqualTo(BatchStatus.COMPLETED);

        Properties seen = ParamRecordingBatchlet.RECORDED.get();
        assertThat(seen)
                .as("batchlet saw the parameters via JobContext.getProperties()")
                .isNotNull();
        assertThat(seen.getProperty("inputFile")).isEqualTo("data.csv");
        assertThat(seen.getProperty("threshold")).isEqualTo("42");
    }
}
