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
package org.os890.jawelte.tests.scope.scenario20;

import jakarta.annotation.Priority;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;
import org.os890.jawelte.module.scope.impl.adapter.context.TestMethodScopeStore;

/**
 * @Priority(50) - in beforeEach runs first (records "BEFORE_50"). In
 * afterEach LIFO order this port's afterEach runs LAST, after
 * scope-module's @Priority(100) afterEach has deactivated the method
 * scope. The probe records whether the scope was already deactivated.
 */
@Priority(50)
public class TestScenarioPort50Probe implements TestModuleLifecyclePort {

    public TestScenarioPort50Probe() {
    }

    @Override
    public void beforeEach(TestContext testContext) {
        Scenario20Subject.RECORDED.add("BEFORE_50");
    }

    @Override
    public void afterEach(TestContext testContext) {
        boolean scopeDeactivated = testContext.getMetadata(TestMethodScopeStore.class)
                .map(store -> store.map() == null)
                .orElse(false);
        Scenario20Subject.RECORDED.add(scopeDeactivated
                ? "AFTER_50_SCOPE_DEACTIVATED"
                : "AFTER_50_SCOPE_STILL_ACTIVE");
    }
}
