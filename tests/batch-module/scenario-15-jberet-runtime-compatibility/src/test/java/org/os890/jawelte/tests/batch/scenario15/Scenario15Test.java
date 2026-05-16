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
package org.os890.jawelte.tests.batch.scenario15;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.batch.operations.JobOperator;
import jakarta.batch.runtime.BatchRuntime;
import jakarta.batch.runtime.BatchStatus;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.batch.api.BatchExecution;

/**
 * Scenario 15 — cross-runtime compatibility check. JBeret
 * (<code>org.jberet:jberet-core</code>) sits on the test
 * classpath instead of Apache BatchEE. {@code BatchRuntime
 * .getJobOperator()} discovers JBeret's
 * {@code DelegatingJobOperator} via its own
 * {@code META-INF/services/jakarta.batch.operations.JobOperator}
 * file, the active CDI runtime picks up JBeret's portable
 * extension, and {@link org.os890.jawelte.module.batch.impl.BatchExecutionObserver}
 * drives the JSL job to {@link BatchStatus#COMPLETED} without
 * any code change.
 *
 * <p>The first assertion reads {@link BatchRuntime#getJobOperator()}
 * directly (not the CDI-proxied {@link JobOperator} bean — that
 * one's runtime class is the proxy, not the underlying impl) and
 * confirms it lives in the {@code org.jberet.*} package: proof
 * that we really did pick the JBeret implementation off this
 * scenario's classpath.
 */
@EnableTestBeans
class Scenario15Test {

    @Inject
    private Event<BatchExecution> batchEvent;

    @Test
    void jberetRuntimeRunsTheJobToCompletion() {
        JobOperator operator = BatchRuntime.getJobOperator();
        assertThat(operator.getClass().getName())
                .as("BatchRuntime.getJobOperator() resolves JBeret's impl on this scenario's classpath")
                .startsWith("org.jberet.");

        BatchExecution execution = new BatchExecution("simple-job");
        batchEvent.fire(execution);

        assertThat(execution.getStatus())
                .as("JBeret drove the job to BatchStatus.COMPLETED through the same observer")
                .isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getJobExecution())
                .as("event populated by the observer regardless of which runtime is active")
                .isNotNull();
        assertThat(execution.getExecutionId())
                .as("executionId matches the JBeret-assigned id on the snapshot")
                .isEqualTo(execution.getJobExecution().getExecutionId());
    }
}
