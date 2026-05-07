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
package org.os890.jawelte.tests.jpa.scenario50;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Verifies that per-method cleanup wipes the auto-generated
 * {@code person_hobby} join table — the JPQL-DELETE strategy
 * wouldn't touch it because it's not a mapped {@code @Entity},
 * but the TRUNCATE strategy walks
 * {@code INFORMATION_SCHEMA.TABLES} and catches every
 * {@code PUBLIC} schema table.
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Scenario50Test {

    @Inject
    private PersonHobbyService personHobbyService;

    /** No-arg constructor for CDI. */
    public Scenario50Test() {
    }

    /** Persist person + 2 hobbies; assert all three tables populated. */
    @Test
    @Order(1)
    public void persistPersonWithHobbiesPopulatesAllTables() {
        personHobbyService.persistPersonWithTwoHobbies("alice");
        assertThat(personHobbyService.countPeople()).isEqualTo(1L);
        assertThat(personHobbyService.countHobbies()).isEqualTo(2L);
        assertThat(personHobbyService.countJoinTableRows())
                .as("join table should hold one row per (person, hobby) pair")
                .isEqualTo(2L);
    }

    /** Per-method cleanup wiped both tables AND the join table. */
    @Test
    @Order(2)
    public void perMethodCleanupWipesJoinTableToo() {
        assertThat(personHobbyService.countPeople()).isEqualTo(0L);
        assertThat(personHobbyService.countHobbies()).isEqualTo(0L);
        assertThat(personHobbyService.countJoinTableRows())
                .as("auto-generated join table must be cleaned by the TRUNCATE strategy")
                .isEqualTo(0L);
    }
}
