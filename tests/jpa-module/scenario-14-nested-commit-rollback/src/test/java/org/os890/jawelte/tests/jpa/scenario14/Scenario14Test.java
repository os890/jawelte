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
package org.os890.jawelte.tests.jpa.scenario14;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Nested {@code @Transactional}: inner commits its own tx then outer rolls back.
 * Each level has its own EntityManager / EntityTransaction, so inner's commit is
 * already in the DB by the time outer rolls back — only outer's persist is
 * discarded. Inner row stays.
 */
@EnableTestBeans
public class Scenario14Test {

    @Inject
    private OuterService outerService;

    /** No-arg constructor for CDI. */
    public Scenario14Test() {
    }

    /** Inner commit survives outer rollback — only the inner row remains. */
    @Test
    public void innerCommitSurvivesOuterRollback() {
        assertThatThrownBy(() ->
                outerService.outerPersistsCallsInnerThenThrows("outer", "inner-survives"))
                .as("the outer service's RuntimeException must propagate to the caller")
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("scenario-14");

        assertThat(outerService.countCustomers())
                .as("inner's committed row remains; outer's rolled-back persist is gone")
                .isEqualTo(1L);
        assertThat(outerService.singleSurvivorName())
                .as("the single surviving row is inner's, not outer's")
                .isEqualTo("inner-survives");
    }
}
