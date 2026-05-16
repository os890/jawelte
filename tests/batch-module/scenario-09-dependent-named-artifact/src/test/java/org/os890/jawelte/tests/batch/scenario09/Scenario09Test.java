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
package org.os890.jawelte.tests.batch.scenario09;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.batch.runtime.BatchStatus;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.batch.api.BatchExecution;

/**
 * Scenario 09 — a {@code @Dependent @Named} batchlet that
 * {@code @Inject}s an {@code @ApplicationScoped} CDI bean.
 * Confirms the documented artifact shape works under jBatch's CDI
 * resolution path: the runtime looks the batchlet up by
 * {@code @Named} from JSL, instantiates it through the CDI
 * container, and field injection of {@code GreeterBean} succeeds.
 */
@EnableTestBeans
class Scenario09Test {

    @Inject
    private Event<BatchExecution> batchEvent;

    @Test
    void dependentNamedBatchletReceivesCdiInjection() {
        BatchExecution execution = new BatchExecution("injecting-job");

        batchEvent.fire(execution);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(InjectingBatchlet.GREETING.get())
                .as("@Inject GreeterBean was satisfied inside the batchlet")
                .isEqualTo("hello, batch");
    }
}
