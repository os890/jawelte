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
package org.os890.jawelte.tests.jpa.scenario45;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Test scenario #45 — verifies the
 * {@code @Observes ContainerStarted} seeding pattern.
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Scenario45Test {

    @Inject
    private PersonQueryService personQueryService;

    /** No-arg constructor for CDI. */
    public Scenario45Test() {
    }

    /**
     * The seeded {@code Person} is visible to the first test
     * method.
     */
    @Test
    @Order(1)
    public void seededDataIsVisibleToFirstMethod() {
        long count = personQueryService.countByName("seed-Alice");
        assertThat(count)
                .as("seeded person from ContainerStarted observer should be visible")
                .isEqualTo(1L);
    }

    /**
     * Per-method cleanup wiped the seed before this method ran.
     */
    @Test
    @Order(2)
    public void seedIsCleanedUpBetweenMethods() {
        long count = personQueryService.countAll();
        assertThat(count)
                .as("per-method cleanup should have wiped the seed")
                .isEqualTo(0L);
    }
}
