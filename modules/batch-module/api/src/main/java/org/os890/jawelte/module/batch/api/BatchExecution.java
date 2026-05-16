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
package org.os890.jawelte.module.batch.api;

import java.time.Duration;
import java.util.Objects;
import java.util.Properties;

import jakarta.batch.runtime.BatchStatus;
import jakarta.batch.runtime.JobExecution;

/**
 * CDI event class carrying both the <b>request</b> (job name,
 * parameters, timeout) and, after the synchronous observer in
 * {@code batch-module/impl} has driven the job to a terminal status,
 * the <b>result</b> (executionId, {@link JobExecution},
 * {@link BatchStatus}, exit status).
 *
 * <p>Usage shape:
 * <pre>{@code
 *   @Inject
 *   private Event<BatchExecution> batchEvent;
 *
 *   @Test
 *   void runsJob() {
 *       BatchExecution exec = new BatchExecution("simple-job")
 *               .param("input", "data.csv")
 *               .timeout(Duration.ofSeconds(30));
 *       batchEvent.fire(exec);
 *       assertThat(exec.getStatus()).isEqualTo(BatchStatus.COMPLETED);
 *   }
 * }</pre>
 *
 * <p>{@code Event.fire(...)} is observably blocking — the impl
 * module's observer runs synchronously on the test thread, polls
 * the {@code JobOperator} with exponential backoff until a terminal
 * {@link BatchStatus} is reached (or the timeout is exceeded), and
 * then populates this instance with the final {@link JobExecution}
 * before {@code fire(...)} unblocks.
 *
 * <p>This class is intentionally a concrete mutable value carrier:
 * the test thread instantiates it, the impl-side observer mutates
 * the result fields during dispatch (via the internal
 * {@link #markCompleted(long, JobExecution)} hook), and the test
 * thread reads the result back off the same instance. No builder
 * type, no immutable snapshot — the single-threaded
 * fire→observe→read sequence keeps the lifecycle obvious.
 */
public class BatchExecution {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final long DEFAULT_INITIAL_POLL_MS = 50L;

    private final String jobName;
    private final Properties parameters;
    private Duration timeout = DEFAULT_TIMEOUT;

    private boolean completed;
    private long executionId;
    private JobExecution jobExecution;

    /**
     * Create a request for the named job with an empty parameters
     * bag and the default 60-second timeout.
     *
     * @param jobName the JSL job name to start
     * @throws IllegalArgumentException if {@code jobName} is null or empty
     */
    public BatchExecution(String jobName) {
        this(jobName, new Properties());
    }

    /**
     * Create a request for the named job with the supplied initial
     * parameters bag. The {@link Properties} reference is held
     * directly (not defensively copied) so further mutation via
     * {@link #param(String, String)} is visible.
     *
     * @param jobName the JSL job name to start
     * @param parameters the initial job parameters; null is treated as empty
     * @throws IllegalArgumentException if {@code jobName} is null or empty
     */
    public BatchExecution(String jobName, Properties parameters) {
        if (jobName == null || jobName.isEmpty()) {
            throw new IllegalArgumentException("jobName must not be null or empty");
        }
        this.jobName = jobName;
        this.parameters = parameters != null ? parameters : new Properties();
    }

    /**
     * Add (or replace) one job parameter. Cumulative across calls.
     *
     * @param key the parameter key
     * @param value the parameter value
     * @return this instance for fluent chaining
     */
    public BatchExecution param(String key, String value) {
        Objects.requireNonNull(key, "param key");
        Objects.requireNonNull(value, "param value");
        parameters.setProperty(key, value);
        return this;
    }

    /**
     * Override the default timeout (60 seconds). The observer will
     * stop polling and throw {@link IllegalStateException} if the
     * job does not reach a terminal status within this duration.
     * The job itself is <b>not</b> cancelled on timeout — it keeps
     * running on the jBatch thread.
     *
     * @param newTimeout the maximum wait time
     * @return this instance for fluent chaining
     */
    public BatchExecution timeout(Duration newTimeout) {
        Objects.requireNonNull(newTimeout, "timeout");
        this.timeout = newTimeout;
        return this;
    }

    /**
     * Read the JSL job name supplied at construction.
     *
     * @return the JSL job name; never null
     */
    public String getJobName() {
        return jobName;
    }

    /**
     * Read the live job parameters. The reference returned is the
     * same {@link Properties} instance the observer hands to
     * {@code JobOperator.start(...)}, so mutations via
     * {@link #param(String, String)} are visible immediately.
     *
     * @return the job parameters; never null (empty
     *         {@link Properties} if none were added)
     */
    public Properties getJobParameters() {
        return parameters;
    }

    /**
     * Read the configured timeout — either the 60-second default or
     * the value supplied via {@link #timeout(Duration)}.
     *
     * @return the maximum wait time; never null
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Read the initial polling interval. Not configurable from
     * user code; exposed for the observer's polling loop.
     *
     * @return the initial polling interval (50 ms)
     */
    public long getInitialPollMs() {
        return DEFAULT_INITIAL_POLL_MS;
    }

    /**
     * Read the {@code JobOperator}-assigned execution id. The
     * impl-side observer sets this before {@code fire(...)} returns.
     *
     * @return the execution id
     * @throws IllegalStateException if accessed before the event
     *         has been fired and completed
     */
    public long getExecutionId() {
        if (!completed) {
            throw new IllegalStateException(
                    "BatchExecution has not completed yet — fire(...) must run to terminal status first");
        }
        return executionId;
    }

    /**
     * Read the final {@link JobExecution} populated by the observer
     * once the polling loop sees a terminal status.
     *
     * @return the final {@link JobExecution}; null before the event
     *         has been fired
     */
    public JobExecution getJobExecution() {
        return jobExecution;
    }

    /**
     * Convenience accessor for {@code getJobExecution().getBatchStatus()}.
     *
     * @return the terminal {@link BatchStatus}; null before the
     *         event has been fired
     */
    public BatchStatus getStatus() {
        return jobExecution != null ? jobExecution.getBatchStatus() : null;
    }

    /**
     * Convenience accessor for {@code getJobExecution().getExitStatus()}.
     *
     * @return the terminal exit-status string; null before the
     *         event has been fired
     */
    public String getExitStatus() {
        return jobExecution != null ? jobExecution.getExitStatus() : null;
    }

    /**
     * <b>Internal — for the {@code batch-module/impl} observer and
     * its configured {@code TimeoutHandler} SPI only.</b>
     * Marks the run as completed from the observer's perspective and
     * populates the result fields. The {@link JobExecution}
     * typically carries a terminal {@link BatchStatus}
     * ({@code COMPLETED}/{@code FAILED}/{@code STOPPED}/{@code ABANDONED})
     * — but a non-terminal snapshot is also valid when a
     * {@code TimeoutHandler} chose to populate the event with the
     * latest observed state instead of throwing. Calling this from
     * test code is unsupported and breaks the invariant that
     * {@link #getExecutionId()} corresponds to a real
     * {@code JobOperator}-assigned id.
     *
     * @param newExecutionId the {@code JobOperator}-assigned id
     * @param newJobExecution the {@link JobExecution} snapshot
     */
    public void markCompleted(long newExecutionId, JobExecution newJobExecution) {
        Objects.requireNonNull(newJobExecution, "jobExecution");
        this.executionId = newExecutionId;
        this.jobExecution = newJobExecution;
        this.completed = true;
    }
}
