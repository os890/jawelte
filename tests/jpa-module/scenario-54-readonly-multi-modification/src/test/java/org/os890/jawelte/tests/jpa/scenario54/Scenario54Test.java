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
package org.os890.jawelte.tests.jpa.scenario54;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * A single {@code @ReadOnly @Transactional} body packs three
 * heterogeneous mutations — {@code persist}, setter mutation, and
 * {@code remove}. All three must be rolled back together: the
 * inserted row never lands, the setter change never reaches the
 * existing row, and the removed row stays.
 */
@EnableTestBeans
public class Scenario54Test {

    @Inject
    private ItemMultiOpService itemMultiOpService;

    /** No-arg constructor for CDI. */
    public Scenario54Test() {
    }

    /** persist + setter + remove in one @ReadOnly call all roll back. */
    @Test
    public void readOnlyDiscardsAllThreeModifications() {
        Long mutateId = itemMultiOpService.seed("preexisting-A");
        Long removeId = itemMultiOpService.seed("preexisting-B");

        itemMultiOpService.multiModificationUnderReadOnly(mutateId, "mutated", removeId);

        assertThat(itemMultiOpService.countItems())
                .as("inserted row must not survive; removed row must still be there")
                .isEqualTo(2L);
        assertThat(itemMultiOpService.currentName(mutateId))
                .as("setter mutation must not reach the database")
                .isEqualTo("preexisting-A");
        assertThat(itemMultiOpService.currentName(removeId))
                .as("remove must not reach the database — row still there")
                .isEqualTo("preexisting-B");
    }
}
