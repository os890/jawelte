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
package org.os890.jawelte.tests.jpa.scenario16;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Verifies that an {@code @ReadOnly @Transactional} method
 * discards setter-driven dirty mutations on a managed entity.
 *
 * <p>Inside the read-only block, {@code item.setName("modified")}
 * marks the managed entity dirty but the interceptor sets
 * {@code FlushMode.COMMIT} and marks the tx rollback-only — the
 * change is never flushed and never committed. A follow-up read
 * outside the block must observe the original value.
 */
@EnableTestBeans
public class Scenario16Test {

    @Inject
    private ItemService itemService;

    /** No-arg constructor for CDI. */
    public Scenario16Test() {
    }

    /** Setter-driven dirty mutation under @ReadOnly is rolled back. */
    @Test
    public void readOnlyDiscardsSetterDrivenWrites() {
        Long id = itemService.seed("original");

        itemService.mutateViaSetterUnderReadOnly(id, "modified");

        assertThat(itemService.currentName(id))
                .as("setter mutation under @ReadOnly must not reach the database")
                .isEqualTo("original");
    }
}
