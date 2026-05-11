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
package org.os890.jawelte.tests.jta.scenario28;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Port of jpa-module scenario 16 — {@code @ReadOnly} discards writes
 * under JTA. The {@code ReadOnlyInterceptor} (jpa-module/impl) marks
 * the active transaction rollback-only; under JTA that translates to
 * {@code TM.setRollbackOnly()}, and the JTA tx rolls back on commit
 * even though no exception was thrown.
 */
@EnableTestBeans
public class Scenario28Test {

    @Inject
    private ItemService itemService;

    /** No-arg constructor for CDI. */
    public Scenario28Test() {
    }

    @Test
    public void readOnlyDiscardsWritesUnderJta() {
        itemService.persistInsideReadOnly();
        assertThat(itemService.countItems())
                .as("a @ReadOnly @Transactional method's writes must be rolled back at JTA commit time")
                .isZero();
    }
}
