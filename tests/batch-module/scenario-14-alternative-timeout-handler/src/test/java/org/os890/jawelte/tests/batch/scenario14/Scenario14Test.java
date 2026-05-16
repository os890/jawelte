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
package org.os890.jawelte.tests.batch.scenario14;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;

import jakarta.batch.runtime.BatchStatus;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.batch.api.BatchExecution;

/**
 * Scenario 14 — alternative {@code TimeoutHandler} active. This
 * scenario's classpath ships a
 * {@code META-INF/services/org.os890.jawelte.module.batch.api.port.TimeoutHandler}
 * file naming {@code PopulateLatestSnapshotTimeoutHandler}; the
 * project's {@code ServicePriorityResolver} picks it over the
 * default {@code ThrowingTimeoutHandler} because its
 * {@code @Priority} value is numerically lower
 * ({@code Integer.MAX_VALUE - 100} vs {@code Integer.MAX_VALUE}).
 *
 * <p>The batchlet runs for ~3 s; the 500 ms timeout fires while
 * the job is still in a non-terminal status. With the alternative
 * handler installed, {@code Event.fire(...)} does <b>not</b>
 * throw; instead the event is populated with the latest
 * non-terminal {@code JobExecution} snapshot so test code can
 * inspect what the runtime last reported.
 */
@EnableTestBeans
class Scenario14Test {

    @Inject
    private Event<BatchExecution> batchEvent;

    @Test
    void alternativeHandlerPopulatesEventWithoutThrowing() {
        BatchExecution execution = new BatchExecution("long-running-job")
                .timeout(Duration.ofMillis(500L));

        assertThatCode(() -> batchEvent.fire(execution))
                .as("alternative handler does not throw on timeout")
                .doesNotThrowAnyException();

        assertThat(execution.getJobExecution())
                .as("event populated with the latest JobExecution snapshot")
                .isNotNull();
        assertThat(execution.getStatus())
                .as("snapshot's BatchStatus is non-terminal (job was still running when the handler returned)")
                .isIn(BatchStatus.STARTING, BatchStatus.STARTED, BatchStatus.STOPPING);
        assertThat(execution.getExecutionId())
                .as("executionId matches the JobOperator-assigned id on the snapshot")
                .isEqualTo(execution.getJobExecution().getExecutionId());
    }
}
