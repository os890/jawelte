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
package org.os890.jawelte.tests.jpa.scenario39;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * jpa-module fires {@code TransactionStarted} → {@code TransactionBeforeCompletion}
 * → {@code TransactionRolledBack} when a {@code @Transactional} method throws.
 * Each event carries the active persistence-unit name.
 */
@EnableTestBeans
public class Scenario39Test {

    @Inject
    private RollbackingService rollbackingService;

    @Inject
    private RollbackEventRecorder rollbackEventRecorder;

    /** No-arg constructor for CDI. */
    public Scenario39Test() {
    }

    /** A rollback fires Started → BeforeCompletion → RolledBack in order. */
    @Test
    public void rollbackFiresThreeEventsInOrderWithPuName() {
        rollbackEventRecorder.reset();

        assertThatThrownBy(rollbackingService::persistAndThrow)
                .as("the service's RuntimeException must propagate")
                .isInstanceOf(RuntimeException.class);

        assertThat(rollbackEventRecorder.events())
                .as("rollback must fire TransactionStarted → TransactionBeforeCompletion "
                        + "→ TransactionRolledBack, each carrying the testPU39 name")
                .containsExactly(
                        "started:testPU39",
                        "before:testPU39",
                        "rolledBack:testPU39");
    }
}
