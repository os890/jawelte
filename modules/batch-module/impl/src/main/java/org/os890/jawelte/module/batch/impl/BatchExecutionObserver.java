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
package org.os890.jawelte.module.batch.impl;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import jakarta.batch.operations.JobOperator;
import jakarta.batch.runtime.BatchStatus;
import jakarta.batch.runtime.JobExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.batch.api.BatchExecution;
import org.os890.jawelte.module.batch.api.port.TimeoutHandler;

/**
 * Synchronous CDI observer for {@link BatchExecution}. Drives every
 * fired event to a terminal {@link BatchStatus} using exponential
 * backoff polling against {@link JobOperator}, then populates the
 * event in place with the final {@link JobExecution} so the test
 * thread sees the result the moment {@code Event.fire(...)}
 * unblocks.
 *
 * <p><b>Polling schedule.</b> Starts at
 * {@link BatchExecution#getInitialPollMs()} (50 ms), doubles each
 * iteration, capped at 5 seconds. The schedule is fixed by the
 * spec'd defaults — there is no MP Config override.
 *
 * <p><b>Terminal statuses.</b> {@link BatchStatus#COMPLETED},
 * {@link BatchStatus#FAILED}, {@link BatchStatus#STOPPED}, and
 * {@link BatchStatus#ABANDONED} end the polling loop. All other
 * statuses ({@code STARTING}, {@code STARTED}, {@code STOPPING})
 * are intermediate and trigger another poll after the current
 * backoff delay.
 *
 * <p><b>Timeout behavior.</b> When the cumulative wall-clock time
 * since the first {@code JobOperator.start(...)} exceeds
 * {@link BatchExecution#getTimeout()}, the observer delegates to
 * the configured {@link TimeoutHandler} SPI (resolved once per JVM
 * via {@code TestContext.loadService(TimeoutHandler.class)}). The
 * default handler throws {@link IllegalStateException} naming the
 * job, the timeout, and the last observed status; consumers swap
 * the behaviour by registering an alternative {@code TimeoutHandler}
 * with a lower numeric {@code @Priority}. Either way, the job
 * itself is <b>not</b> cancelled — it keeps running on the jBatch
 * thread pool.
 *
 * <p><b>Threading.</b> The observer runs on the test thread that
 * called {@code fire(...)}. The jBatch runtime runs the actual
 * job on its own thread pool; this observer only polls the
 * runtime's status view.
 */
@ApplicationScoped
public class BatchExecutionObserver {

    private static final long BACKOFF_CAP_MS = 5000L;

    private static final Logger LOG =
            System.getLogger(BatchExecutionObserver.class.getName());

    private static final TimeoutHandler TIMEOUT_HANDLER =
            TestContext.loadService(TimeoutHandler.class);

    @Inject
    private JobOperator jobOperator;

    /** No-arg constructor required by the CDI runtime. */
    public BatchExecutionObserver() {
    }

    /**
     * Observe a fired {@link BatchExecution}. Starts the job,
     * polls until terminal status (or timeout), and populates the
     * event with the final {@link JobExecution}.
     *
     * @param event the fired event; mutated in place
     */
    public void observe(@Observes BatchExecution event) {
        long executionId = jobOperator.start(event.getJobName(), event.getJobParameters());
        LOG.log(Level.INFO,
                "Started batch job '" + event.getJobName() + "' (executionId=" + executionId + ")");

        long pollMs = event.getInitialPollMs();
        long timeoutMs = event.getTimeout().toMillis();
        long startMs = System.currentTimeMillis();

        while (true) {
            JobExecution snapshot = jobOperator.getJobExecution(executionId);
            BatchStatus status = snapshot.getBatchStatus();
            if (isTerminal(status)) {
                event.markCompleted(executionId, snapshot);
                LOG.log(Level.INFO,
                        "Batch job '" + event.getJobName() + "' finished: " + status
                                + " (exit=" + snapshot.getExitStatus() + ")");
                return;
            }

            long elapsed = System.currentTimeMillis() - startMs;
            if (elapsed >= timeoutMs) {
                TIMEOUT_HANDLER.onTimeout(event, executionId, snapshot);
                return;
            }

            try {
                Thread.sleep(pollMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Polling interrupted for batch job '" + event.getJobName() + "'", ie);
            }

            pollMs = Math.min(pollMs * 2, BACKOFF_CAP_MS);
        }
    }

    private static boolean isTerminal(BatchStatus status) {
        return status == BatchStatus.COMPLETED
                || status == BatchStatus.FAILED
                || status == BatchStatus.STOPPED
                || status == BatchStatus.ABANDONED;
    }
}
