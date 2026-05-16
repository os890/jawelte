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
package org.os890.jawelte.tests.batch.scenario11;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import jakarta.batch.runtime.BatchStatus;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.batch.api.BatchExecution;

/**
 * Scenario 11 — backoff cap. A 6-second job fires through the
 * observer with a 20-second timeout. Without the 5-second cap, the
 * 7th poll would wait 6.4 s and the 8th 12.8 s, pushing observed
 * latency to ~12-13 s past job completion. With the cap, the
 * largest possible post-completion wait is 5 s, so total
 * {@code fire(...)} wall time should be below ~12 s.
 *
 * <p>Tagged {@code "slow"} so users running the wip cycle can
 * exclude it via {@code -Dgroups='!slow'} if iterating quickly.
 */
@EnableTestBeans
@Tag("slow")
class Scenario11Test {

    @Inject
    private Event<BatchExecution> batchEvent;

    @Test
    void backoffCapKeepsLatencyBounded() {
        BatchExecution execution = new BatchExecution("six-second-job")
                .timeout(Duration.ofSeconds(20));

        long startMs = System.currentTimeMillis();
        batchEvent.fire(execution);
        long elapsedMs = System.currentTimeMillis() - startMs;

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(elapsedMs)
                .as("6 s job + at-most-5 s cap-bounded post-completion wait")
                .isLessThan(12_000L);
    }
}
