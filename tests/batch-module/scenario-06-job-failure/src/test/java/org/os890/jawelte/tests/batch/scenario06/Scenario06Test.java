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
package org.os890.jawelte.tests.batch.scenario06;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.batch.runtime.BatchStatus;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.batch.api.BatchExecution;

/**
 * Scenario 06 — verifies that a batchlet exception ends in
 * {@link BatchStatus#FAILED}, which the observer treats as a
 * terminal status and populates the event normally (no exception
 * leaks from {@code Event.fire(...)}).
 */
@EnableTestBeans
class Scenario06Test {

    @Inject
    private Event<BatchExecution> batchEvent;

    @Test
    void jobFailureSurfacesAsFailedStatus() {
        BatchExecution execution = new BatchExecution("fail-job");

        batchEvent.fire(execution);

        assertThat(execution.getStatus())
                .as("batchlet throwing → BatchStatus.FAILED, not an observer exception")
                .isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getJobExecution())
                .as("event is populated with the failed JobExecution snapshot")
                .isNotNull();
    }
}
