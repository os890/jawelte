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
package org.os890.jawelte.tests.jpa.scenario13;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;


import io.quarkus.test.junit.QuarkusTest;
/**
 * Nested {@code @Transactional}: outer + inner each persist on their own
 * EntityManager / EntityTransaction; both commit; both rows are visible
 * afterwards (mirrors POC's {@code NestedTransactionalTest.nestedBothCommit}).
 */
@EnableTestBeans
@QuarkusTest
public class Scenario13Test {

    @Inject
    private OuterService outerService;

    /** No-arg constructor for CDI. */
    public Scenario13Test() {
    }

    /** Outer + inner both commit → 2 rows visible from a fresh tx. */
    @Test
    public void outerAndInnerBothCommitProducesTwoRows() {
        outerService.outerPersistsAndCallsInner("outer", "inner");

        assertThat(outerService.countCustomers())
                .as("nested commit/commit must leave both customers in the DB")
                .isEqualTo(2L);
    }
}
