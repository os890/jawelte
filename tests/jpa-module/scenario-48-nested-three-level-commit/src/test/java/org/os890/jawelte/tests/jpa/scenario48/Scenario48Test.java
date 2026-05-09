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
package org.os890.jawelte.tests.jpa.scenario48;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Three nested {@code @Transactional} invocations (level 1 →
 * level 2 → level 3, each in a different
 * {@code @ApplicationScoped} bean so the CDI interceptor fires);
 * each level persists one {@link Person}; all three rows commit.
 */
@EnableTestBeans
public class Scenario48Test {

    @Inject
    private OuterLevelService outerLevelService;

    /** No-arg constructor for CDI. */
    public Scenario48Test() {
    }

    /** Drive the three-level nest and assert all three rows survived. */
    @Test
    public void allThreeNestedLevelsCommit() {
        outerLevelService.persistThreeLevels("L1", "L2", "L3");
        assertThat(outerLevelService.countPeople()).isEqualTo(3L);
    }
}
