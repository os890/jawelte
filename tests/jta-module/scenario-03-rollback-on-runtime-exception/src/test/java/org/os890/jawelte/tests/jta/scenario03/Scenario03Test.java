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
package org.os890.jawelte.tests.jta.scenario03;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Ticket-006 scenario #03 — rollback on {@link RuntimeException}.
 * The {@code @Transactional} method persists a {@link Marker} and then
 * throws; the JTA strategy rolls back so the row is not committed.
 * Per the project-wide interceptor convention, both checked and
 * unchecked exceptions roll back (TICKET-005 §"Rollback rule").
 */
@EnableTestBeans
public class Scenario03Test {

    @Inject
    private MarkerService markerService;

    /** No-arg constructor for CDI. */
    public Scenario03Test() {
    }

    @Test
    public void runtimeExceptionRollsBackJtaTransaction() {
        assertThatThrownBy(markerService::persistAndThrow)
                .as("the service's RuntimeException must propagate to the caller")
                .isInstanceOf(RuntimeException.class)
                .hasMessage("intentional rollback driver");

        long count = markerService.countMarkers();
        assertThat(count)
                .as("rolled-back JTA tx must leave zero rows committed")
                .isZero();
    }
}
