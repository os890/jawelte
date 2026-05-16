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

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import jakarta.annotation.Priority;
import jakarta.batch.runtime.JobExecution;

import org.os890.jawelte.module.batch.api.BatchExecution;
import org.os890.jawelte.module.batch.api.port.TimeoutHandler;

/**
 * Opt-in {@link TimeoutHandler} that returns the latest observed
 * {@link JobExecution} snapshot instead of throwing when the
 * polling loop exceeds {@link BatchExecution#getTimeout()}.
 *
 * <p>Logs a {@code WARNING} naming the job, the executionId, and
 * the last observed {@code BatchStatus}, then calls
 * {@link BatchExecution#complete(long, JobExecution)} with the
 * non-terminal snapshot. Test code sees a populated event after
 * {@code Event.fire(...)} returns and inspects
 * {@link BatchExecution#getStatus()} to react to whatever
 * intermediate status the job was in
 * ({@code STARTED}, {@code STARTING}, {@code STOPPING}, etc.).
 *
 * <p><b>Not registered by default.</b> This module's
 * {@code META-INF/services/org.os890.jawelte.module.batch.api.port.TimeoutHandler}
 * file names only {@link ThrowingTimeoutHandler}. Consumers
 * activate this handler by shipping their own
 * {@code META-INF/services} file that names this class's FQCN.
 * The {@code @Priority(Integer.MAX_VALUE - 100)} value below is
 * numerically lower than the default's
 * {@code Integer.MAX_VALUE}, so the explicit registration wins
 * the {@code ServicePriorityResolver} sort.
 *
 * <p>The job continues running on the jBatch thread pool after
 * this handler returns — the timeout is an "observer stops
 * waiting" signal, not a cancellation. Consumers needing
 * deterministic cleanup read
 * {@link BatchExecution#getExecutionId()} on the populated event
 * and call {@code JobOperator.stop(executionId)} themselves.
 */
@Priority(Integer.MAX_VALUE - 100)
public class PopulateLatestSnapshotTimeoutHandler implements TimeoutHandler {

    private static final Logger LOG =
            System.getLogger(PopulateLatestSnapshotTimeoutHandler.class.getName());

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public PopulateLatestSnapshotTimeoutHandler() {
    }

    @Override
    public void onTimeout(BatchExecution event, long executionId, JobExecution latestSnapshot) {
        LOG.log(Level.WARNING,
                "Timeout waiting for batch job '" + event.getJobName()
                        + "' (executionId=" + executionId
                        + ", lastStatus=" + latestSnapshot.getBatchStatus()
                        + "); populating event with latest snapshot without throwing");
        event.complete(executionId, latestSnapshot);
    }
}
