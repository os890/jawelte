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
package org.os890.jawelte.tests.jta.scenario42;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * {@code @ReadOnly} rolls back setter modifications on already-loaded
 * entities — not just {@code persist()} calls. Writable
 * {@code @Transactional} creates an item; a follow-up
 * {@code @ReadOnly} method renames it via setter; the post-condition
 * is that the original name is still in the DB.
 */
@EnableTestBeans
public class Scenario42Test {

    @Inject
    private ItemService service;

    /** No-arg constructor for CDI. */
    public Scenario42Test() {
    }

    @Test
    public void readOnlyRollsBackSetterModification() {
        service.createItem("original");
        service.renameInReadOnly("original", "tampered");
        assertThat(service.findName("original"))
                .as("the @ReadOnly setter modification must be rolled back at JTA commit")
                .isEqualTo("original");
        assertThat(service.findName("tampered"))
                .as("no row with the @ReadOnly-attempted new name should exist")
                .isNull();
    }
}
