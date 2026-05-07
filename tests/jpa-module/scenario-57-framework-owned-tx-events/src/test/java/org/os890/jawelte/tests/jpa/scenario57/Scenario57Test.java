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
package org.os890.jawelte.tests.jpa.scenario57;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * jpa-module's tx events are wired to the framework-owned scope
 * only. {@code @Transactional} produces a complete event sequence
 * (Started + BeforeCompletion + Committed); a user-driven path
 * that pulls an {@code EntityManager} from the injected
 * {@code EntityManagerFactory} and drives its
 * {@code EntityTransaction} directly bypasses the strategy and
 * fires nothing.
 */
@EnableTestBeans
public class Scenario57Test {

    @Inject
    private MarkerService markerService;

    @Inject
    private TxEventRecorder txEventRecorder;

    /** No-arg constructor for CDI. */
    public Scenario57Test() {
    }

    /** @Transactional fires all three events; user-driven path fires none. */
    @Test
    public void onlyFrameworkDrivenTxFireEvents() {
        txEventRecorder.reset();

        markerService.frameworkDriven();

        assertThat(txEventRecorder.started())
                .as("framework-driven @Transactional fires TransactionStarted once")
                .isEqualTo(1);
        assertThat(txEventRecorder.beforeCompletion())
                .as("framework-driven @Transactional fires TransactionBeforeCompletion once")
                .isEqualTo(1);
        assertThat(txEventRecorder.committed())
                .as("framework-driven @Transactional fires TransactionCommitted once")
                .isEqualTo(1);
        assertThat(txEventRecorder.rolledBack())
                .as("a successful @Transactional must not fire TransactionRolledBack")
                .isEqualTo(0);

        markerService.userDriven();

        assertThat(txEventRecorder.started())
                .as("raw EMF + EntityTransaction bypasses the strategy — counts unchanged")
                .isEqualTo(1);
        assertThat(txEventRecorder.beforeCompletion()).isEqualTo(1);
        assertThat(txEventRecorder.committed()).isEqualTo(1);
        assertThat(txEventRecorder.rolledBack()).isEqualTo(0);
    }
}
