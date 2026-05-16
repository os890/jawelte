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
package org.os890.jawelte.tests.batch.scenario05;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.batch.api.BatchExecution;

/**
 * Scenario 05 — verifies the fluent builder on {@link BatchExecution}
 * directly, without firing it through CDI:
 *
 * <ul>
 *   <li>{@code param(k, v)} accumulates across calls (last write
 *       wins on duplicate keys).</li>
 *   <li>{@code timeout(Duration)} overrides the default 60 s.</li>
 *   <li>Every fluent call returns the same instance — chaining
 *       composes without intermediate allocations.</li>
 *   <li>The default initial-poll interval is 50 ms.</li>
 *   <li>Constructor rejects null / empty {@code jobName}.</li>
 *   <li>{@link BatchExecution#getExecutionId()} on an un-fired
 *       event throws {@link IllegalStateException} (invariant
 *       enforcement).</li>
 * </ul>
 */
@EnableTestBeans
class Scenario05Test {

    @Test
    void fluentBuilderAccumulatesAndOverrides() {
        BatchExecution execution = new BatchExecution("job")
                .param("a", "1")
                .param("b", "2")
                .timeout(Duration.ofSeconds(30));

        assertThat(execution.getJobName()).isEqualTo("job");
        assertThat(execution.getJobParameters().getProperty("a")).isEqualTo("1");
        assertThat(execution.getJobParameters().getProperty("b")).isEqualTo("2");
        assertThat(execution.getTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(execution.getInitialPollMs()).isEqualTo(50L);
    }

    @Test
    void fluentCallsReturnSameInstance() {
        BatchExecution execution = new BatchExecution("job");
        assertThat(execution.param("k", "v")).isSameAs(execution);
        assertThat(execution.timeout(Duration.ofSeconds(10))).isSameAs(execution);
    }

    @Test
    void constructorRejectsEmptyJobName() {
        assertThatThrownBy(() -> new BatchExecution(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jobName");
        assertThatThrownBy(() -> new BatchExecution(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jobName");
    }

    @Test
    void executionIdBeforeFireThrows() {
        BatchExecution execution = new BatchExecution("job");
        assertThatThrownBy(execution::getExecutionId)
                .isInstanceOf(IllegalStateException.class);
    }
}
