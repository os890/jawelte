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

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.spi.BeanManager;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;
import org.os890.jawelte.module.scope.api.TestMethodScoped;

/**
 * @Priority(101) - sits immediately after scope-module's
 * ScopeLifecycleAdapter (@Priority(100)). Records "SCOPE_BEFORE_EACH"
 * the moment the method scope is active and visible via the
 * BeanManager's TestMethodScoped context. Probing the live context
 * here proves scope-module's beforeEach already ran.
 */
@Priority(101)
public class TestScenarioPortScopeProbe implements TestModuleLifecyclePort {

    public TestScenarioPortScopeProbe() {
    }

    @Override
    public void beforeEach(TestContext testContext) {
        SeContainer container = testContext.getMetadata(SeContainer.class).orElseThrow();
        BeanManager beanManager = container.getBeanManager();
        if (beanManager.getContext(TestMethodScoped.class).isActive()) {
            CallbackOrder.RECORDED.add("SCOPE_BEFORE_EACH");
        }
    }
}
