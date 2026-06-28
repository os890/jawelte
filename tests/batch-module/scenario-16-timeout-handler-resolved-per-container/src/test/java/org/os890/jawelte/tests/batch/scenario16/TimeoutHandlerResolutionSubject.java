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
package org.os890.jawelte.tests.batch.scenario16;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.batch.runtime.BatchStatus;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.batch.api.BatchExecution;

/**
 * Subject driven once per container by {@link Scenario16Test}. Firing a
 * (completing) job makes the {@code @ApplicationScoped}
 * {@code BatchExecutionObserver} resolve its {@link
 * org.os890.jawelte.module.batch.api.port.TimeoutHandler} — once per
 * container after the fix, vs once per JVM on the static-field
 * implementation.
 */
@EnableTestBeans
public class TimeoutHandlerResolutionSubject {

    @Inject
    private Event<BatchExecution> batchEvent;

    public TimeoutHandlerResolutionSubject() {
    }

    @Test
    void firesAJobSoTheObserverResolvesItsTimeoutHandler() {
        BatchExecution execution = new BatchExecution("counting-job");
        batchEvent.fire(execution);
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }
}
