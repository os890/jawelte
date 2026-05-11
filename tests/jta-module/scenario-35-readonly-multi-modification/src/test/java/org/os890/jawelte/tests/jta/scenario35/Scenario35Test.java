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
package org.os890.jawelte.tests.jta.scenario35;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Port of jpa-module scenario 54 — multiple writes inside one
 * {@code @ReadOnly @Transactional} method are all discarded at JTA
 * commit time.
 */
@EnableTestBeans
public class Scenario35Test {

    @Inject
    private ItemMultiOpService service;

    /** No-arg constructor for CDI. */
    public Scenario35Test() {
    }

    @Test
    public void readOnlyDiscardsEveryWrite() {
        service.persistMany(5);
        assertThat(service.countItems())
                .as("every write inside a @ReadOnly @Transactional must be rolled back at JTA commit")
                .isZero();
    }
}
