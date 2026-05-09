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
package org.os890.jawelte.tests.jpa.scenario51;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Self-FK cleanup: the {@code person} table holds a foreign-key
 * column referring back to itself ({@code parent_id}). Naive
 * {@code DELETE FROM person} would fail because children block
 * parent deletion; the TRUNCATE-with-RI-off strategy disables
 * referential integrity, truncates, and re-enables RI — which
 * works regardless of the order rows would be deleted in.
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Scenario51Test {

    @Inject
    private PersonHierarchyService personHierarchyService;

    /** No-arg constructor for CDI. */
    public Scenario51Test() {
    }

    /** Persist a 2-level hierarchy; assert two rows present. */
    @Test
    @Order(1)
    public void persistHierarchyPopulatesTable() {
        personHierarchyService.persistTwoLevelHierarchy();
        assertThat(personHierarchyService.countPeople()).isEqualTo(2L);
    }

    /** Cleanup wiped the self-referencing table. */
    @Test
    @Order(2)
    public void cleanupHandlesSelfFk() {
        assertThat(personHierarchyService.countPeople())
                .as("self-FK table must be wiped by the TRUNCATE-with-RI-off strategy")
                .isEqualTo(0L);
    }
}
