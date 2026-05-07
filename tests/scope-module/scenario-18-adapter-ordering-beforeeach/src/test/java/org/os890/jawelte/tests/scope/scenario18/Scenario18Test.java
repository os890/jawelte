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
package org.os890.jawelte.tests.scope.scenario18;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

@EnableTestBeans
class Scenario18Test {

    @Test
    void beforeEachOrderingFollowsAscendingPriority() {
        // Just exercise the lifecycle - all assertions go in @AfterAll
        // so they're independent of test-method order.
    }

    @AfterAll
    static void assertBeforeEachRanInAscendingPriorityOrder() {
        // Expected order (priority ascending):
        //   50 → 100 (scope-module's ScopeLifecycleAdapter, observed by 101) → 200
        // The probe at priority 101 records SCOPE_BEFORE_EACH only when
        // the TestMethodScoped context is active, proving scope-module's
        // beforeEach (priority 100) ran before it.
        assertThat(CallbackOrder.RECORDED)
                .containsExactly("PORT_50_BEFORE_EACH", "SCOPE_BEFORE_EACH", "PORT_200_BEFORE_EACH");
    }
}
