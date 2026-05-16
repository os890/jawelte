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
package org.os890.jawelte.tests.batch.scenario03;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import jakarta.batch.runtime.BatchStatus;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.batch.api.BatchExecution;

/**
 * Scenario 03 — verifies the success path with a user-supplied
 * {@code .timeout(Duration)}. The batchlet sleeps for 500 ms; the
 * configured timeout (5 s) leaves a comfortable margin so the
 * observer reaches the terminal status before the timeout fires.
 */
@EnableTestBeans
class Scenario03Test {

    @Inject
    private Event<BatchExecution> batchEvent;

    @Test
    void customTimeoutAccommodatesLongerJob() {
        BatchExecution execution = new BatchExecution("slow-job")
                .timeout(Duration.ofSeconds(5));

        batchEvent.fire(execution);

        assertThat(execution.getStatus())
                .as("job completes well within the custom 5s timeout")
                .isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getTimeout())
                .as("the user-supplied timeout is preserved on the event")
                .isEqualTo(Duration.ofSeconds(5));
    }
}
