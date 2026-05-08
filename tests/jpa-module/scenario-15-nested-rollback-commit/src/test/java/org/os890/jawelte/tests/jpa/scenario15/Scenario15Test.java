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
package org.os890.jawelte.tests.jpa.scenario15;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Nested {@code @Transactional}: inner rolls back; outer catches the inner
 * exception and commits. Inner's row was discarded by the inner interceptor;
 * outer's row survives. Mirrors POC's
 * {@code NestedTransactionalTest.innerThrowsOuterCatches}.
 */
@EnableTestBeans
public class Scenario15Test {

    @Inject
    private OuterService outerService;

    /** No-arg constructor for CDI. */
    public Scenario15Test() {
    }

    /** Outer survives inner rollback — only outer's row remains. */
    @Test
    public void outerCommitSurvivesInnerRollback() {
        outerService.outerPersistsCatchesInnerRollback("outer-survives", "inner-discarded");

        assertThat(outerService.countCustomers())
                .as("inner's persist was rolled back; outer's persist committed")
                .isEqualTo(1L);
        assertThat(outerService.singleSurvivorName())
                .as("the single surviving row is outer's, not inner's")
                .isEqualTo("outer-survives");
    }
}
