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
package org.os890.jawelte.tests.batch.scenario04;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.batch.api.BatchExecution;

/**
 * Scenario 04 — verifies the timeout-exceeded path. The batchlet
 * is still running when the observer's 500 ms timeout elapses; the
 * observer throws {@link IllegalStateException} whose message
 * names the job, the timeout, and the last observed status.
 *
 * <p>The exception propagates from {@code Event.fire(...)}.
 */
@EnableTestBeans
class Scenario04Test {

    @Inject
    private Event<BatchExecution> batchEvent;

    @Test
    void timeoutExceededThrows() {
        BatchExecution execution = new BatchExecution("hang-job")
                .timeout(Duration.ofMillis(500L));

        assertThatThrownBy(() -> batchEvent.fire(execution))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hang-job")
                .hasMessageContaining("did not complete within")
                .hasMessageContaining("PT0.5S")
                .hasMessageContaining("Last status:");
    }
}
