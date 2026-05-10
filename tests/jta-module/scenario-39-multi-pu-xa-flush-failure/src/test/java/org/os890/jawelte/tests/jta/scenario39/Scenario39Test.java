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
package org.os890.jawelte.tests.jta.scenario39;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * XA atomicity headline: when one persistence unit's flush fails
 * inside a multi-PU JTA transaction, every other enlisted PU rolls
 * back too. The {@code @Transactional} method persists a valid row to
 * PU "a" and an invalid (NOT-NULL-violating) row to PU "b" — PU "b"'s
 * commit-time flush throws, the JTA TM aborts the global tx, and
 * neither PU ends up with a committed row.
 */
@EnableTestBeans
public class Scenario39Test {

    @Inject
    private FlushFailureService service;

    /** No-arg constructor for CDI. */
    public Scenario39Test() {
    }

    @Test
    public void flushFailureInOnePuRollsBackAllPus() {
        assertThatThrownBy(service::writeBothPusBFlushFails)
                .as("the audit-PU flush must propagate as an exception")
                .isInstanceOf(Exception.class);

        assertThat(service.countInPuA())
                .as("PU 'a' must roll back when PU 'b' flush fails (XA atomicity)")
                .isZero();
        assertThat(service.countInPuB())
                .as("PU 'b' must roll back its own failed write")
                .isZero();
    }
}
