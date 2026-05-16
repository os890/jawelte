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
package org.os890.jawelte.module.batch.api.port;

import jakarta.batch.runtime.JobExecution;

import org.os890.jawelte.module.batch.api.BatchExecution;

/**
 * SPI port invoked by the observer when the polling loop exceeds
 * {@link BatchExecution#getTimeout()} before the job reaches a
 * terminal {@code BatchStatus}. The handler chooses how the
 * blocking {@code Event.fire(...)} call returns to test code:
 * either by throwing (signalling "the job did not finish in
 * time") or by populating the event with the latest observed
 * snapshot and returning normally (signalling "I'm done waiting,
 * here is what the runtime last reported").
 *
 * <h2>Discovery</h2>
 *
 * <p>Resolved via
 * {@code TestContext.loadService(TimeoutHandler.class)} —
 * the project-wide canonical SPI lookup. The
 * {@code ServicePriorityResolver} picks the implementation with
 * the lowest numeric {@code jakarta.annotation.Priority}; the
 * default shipped in {@code batch-module/impl}'s
 * {@code META-INF/services} file carries
 * {@code @Priority(Integer.MAX_VALUE)} and loses to any other
 * registration.
 *
 * <h2>Activating an alternative</h2>
 *
 * <p>Consumers swap the default by shipping their own
 * {@code META-INF/services/org.os890.jawelte.module.batch.api.port.TimeoutHandler}
 * file naming the alternative impl's FQCN. The alternative impl's
 * {@code @Priority} value must be numerically lower than the
 * default's {@code Integer.MAX_VALUE}. Lifetime is JVM scope —
 * the observer caches the resolved instance in a static field.
 *
 * <p>This module ships the alternative
 * {@code PopulateLatestSnapshotTimeoutHandler} (POC-style
 * behaviour: populate event with the latest snapshot and return
 * without throwing) in {@code batch-module/impl} for consumers to
 * register; it is <strong>not</strong> active by default.
 */
public interface TimeoutHandler {

    /**
     * Called by the observer when the polling loop has exceeded
     * {@link BatchExecution#getTimeout()} while the job is still
     * in a non-terminal status.
     *
     * <p>The handler may either:
     * <ul>
     *   <li>throw a {@link RuntimeException} — propagated to the
     *       test thread out of {@code Event.fire(...)}, or
     *   <li>populate the event by calling
     *       {@link BatchExecution#complete(long, JobExecution)}
     *       and return normally — the test thread sees a populated
     *       event with the latest (non-terminal)
     *       {@link JobExecution} and decides what to do.
     * </ul>
     *
     * @param event           the fired event
     * @param executionId     the {@code JobOperator}-assigned id
     * @param latestSnapshot  the most recent {@link JobExecution}
     *                        the observer pulled from
     *                        {@code JobOperator.getJobExecution(executionId)}
     *                        — never null
     */
    void onTimeout(BatchExecution event, long executionId, JobExecution latestSnapshot);
}
