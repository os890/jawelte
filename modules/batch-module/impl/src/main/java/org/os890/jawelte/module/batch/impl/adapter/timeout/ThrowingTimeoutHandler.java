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
package org.os890.jawelte.module.batch.impl.adapter.timeout;

import jakarta.annotation.Priority;
import jakarta.batch.runtime.JobExecution;

import org.os890.jawelte.module.batch.api.BatchExecution;
import org.os890.jawelte.module.batch.api.port.TimeoutHandler;

/**
 * Default {@link TimeoutHandler} shipped by
 * {@code batch-module/impl}. Throws an {@link IllegalStateException}
 * naming the job, the timeout, and the last observed
 * {@code BatchStatus} when the polling loop exceeds
 * {@link BatchExecution#getTimeout()}.
 *
 * <p>{@code @Priority(Integer.MAX_VALUE)} — the default-fallback
 * priority. Loses to any alternative {@link TimeoutHandler} the
 * consumer explicitly registers via their own
 * {@code META-INF/services} file, per the project's
 * {@code ServicePriorityResolver} convention.
 *
 * <p>The job continues running on the jBatch thread pool after
 * this handler throws — the timeout is an "observer stops
 * waiting" signal, not a cancellation. Consumers needing
 * deterministic cleanup catch the exception and call
 * {@code JobOperator.stop(executionId)} themselves.
 */
@Priority(Integer.MAX_VALUE)
public class ThrowingTimeoutHandler implements TimeoutHandler {

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public ThrowingTimeoutHandler() {
    }

    @Override
    public void onTimeout(BatchExecution event, long executionId, JobExecution latestSnapshot) {
        throw new IllegalStateException(
                "Batch job '" + event.getJobName()
                        + "' did not complete within " + event.getTimeout()
                        + ". Last status: " + latestSnapshot.getBatchStatus());
    }
}
