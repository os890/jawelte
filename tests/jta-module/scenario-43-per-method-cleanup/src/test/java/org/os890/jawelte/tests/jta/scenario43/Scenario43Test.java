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
package org.os890.jawelte.tests.jta.scenario43;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * jpa-module's {@code DbCleanupStrategy} runs in {@code afterEach}
 * and truncates every table before the next test method starts —
 * under JTA the same way as under RESOURCE_LOCAL. Two ordered
 * methods: the first persists rows, the second asserts a clean
 * table.
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Scenario43Test {

    @Inject
    private MarkerService service;

    /** No-arg constructor for CDI. */
    public Scenario43Test() {
    }

    @Test
    @Order(1)
    public void firstMethodPersistsRows() {
        service.persistOne();
        service.persistOne();
        assertThat(service.count())
                .as("the first ordered test method commits two rows")
                .isEqualTo(2L);
    }

    @Test
    @Order(2)
    public void secondMethodSeesEmptyTable() {
        assertThat(service.count())
                .as("per-method DbCleanupStrategy must wipe the table between @Test methods under JTA")
                .isZero();
    }
}
