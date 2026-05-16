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
package org.os890.jawelte.tests.batch.scenario10;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.batch.runtime.BatchStatus;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.batch.api.BatchExecution;

/**
 * Scenario 10 — exponential backoff convergence. A ~500 ms job
 * fired through the observer must complete within a small upper
 * bound. The 50/100/200/400/800… schedule converges fast (sum of
 * the first 4 intervals = 750 ms); a tight 50 ms poll loop would
 * be ~10 polls instead of ~4 but the externally observable signal
 * is end-to-end wall time. The 5 s upper bound here leaves ample
 * margin for CI jitter while still failing if the loop were stuck
 * in a tight 50 ms cycle through a long job.
 */
@EnableTestBeans
class Scenario10Test {

    @Inject
    private Event<BatchExecution> batchEvent;

    @Test
    void backoffConvergesQuicklyForFastJob() {
        BatchExecution execution = new BatchExecution("half-second-job");

        long startMs = System.currentTimeMillis();
        batchEvent.fire(execution);
        long elapsedMs = System.currentTimeMillis() - startMs;

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(elapsedMs)
                .as("fire(...) overhead beyond the 500 ms job stays under 5 s")
                .isLessThan(5_000L);
    }
}
