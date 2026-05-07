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
package org.os890.jawelte.tests.scope.scenario19;

import jakarta.annotation.Priority;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;
import org.os890.jawelte.module.scope.impl.adapter.context.TestMethodScopeStore;

@Priority(50)
public class Port50 implements TestModuleLifecyclePort {

    public Port50() {
    }

    @Override
    public void afterEach(TestContext testContext) {
        // LIFO ordering: by the time @Priority(50) afterEach runs,
        // scope-module's @Priority(100) afterEach has already run and
        // nulled the method-scope store's map.
        boolean scopeStoreNulled = testContext.getMetadata(TestMethodScopeStore.class)
                .map(store -> store.map() == null)
                .orElse(false);
        CallbackOrder.RECORDED.add(scopeStoreNulled
                ? "AFTER_50_AFTER_SCOPE_DEACTIVATED"
                : "AFTER_50_BUT_SCOPE_STILL_ACTIVE");
    }
}
