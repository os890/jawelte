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
package org.os890.jawelte.tests.batch.scenario08;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.batch.runtime.BatchStatus;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.batch.api.BatchExecution;

/**
 * Scenario 08 — two {@code Event.fire(...)} calls in the same test
 * method. Because the observer runs synchronously, the first
 * {@code fire(...)} blocks until job-a completes; only then does
 * the second fire start job-b. The {@link SequenceRecorder} static
 * captures the start order on the batchlet thread; assertion checks
 * that order matches the {@code fire(...)} order.
 */
@EnableTestBeans
class Scenario08Test {

    @Inject
    private Event<BatchExecution> batchEvent;

    @Test
    void firesBlockBetweenJobs() {
        SequenceRecorder.STARTS.clear();

        BatchExecution first = new BatchExecution("job-a");
        BatchExecution second = new BatchExecution("job-b");

        batchEvent.fire(first);
        batchEvent.fire(second);

        assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(SequenceRecorder.STARTS)
                .as("batchlet starts observed in fire order — second fire waited for first")
                .containsExactly("A", "B");
    }
}
