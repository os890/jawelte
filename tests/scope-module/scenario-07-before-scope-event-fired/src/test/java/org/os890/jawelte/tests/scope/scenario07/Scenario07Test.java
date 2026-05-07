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
package org.os890.jawelte.tests.scope.scenario07;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

@EnableTestBeans
class Scenario07Test {

    @Test
    void firstMethodIsAVehicleForTheLifecycle() {
    }

    @Test
    void secondMethodIsAVehicleForTheLifecycle() {
    }

    @AfterAll
    static void assertExactlyOneEventPerMethodForMethodScope() {
        // Two test methods, each driving ScopeLifecycleAdapter.beforeEach
        // which fires BeforeScopeStarted(TestMethodScoped.class) exactly
        // once. cdi-module also fires BeforeScopeStarted(RequestScoped.class)
        // per method (TICKET-003); that's covered separately. Scenario 17
        // covers the no-event-for-TestClassScoped guarantee.
        assertThat(MethodScopeStartObserver.METHOD_SCOPE_START_COUNT.get())
                .as("BeforeScopeStarted(TestMethodScoped) must fire exactly once per @Test method")
                .isEqualTo(2);
    }
}
