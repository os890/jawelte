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
package org.os890.jawelte.tests.cdi.scenario33;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.control.RequestContextController;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;

public class TestScenarioCapturingLifecyclePort implements TestModuleLifecyclePort {

    public static final AtomicBoolean CONTROLLER_BOUND_AFTER_BEFORE_EACH = new AtomicBoolean();
    public static final AtomicInteger AFTER_EACH_INVOCATIONS = new AtomicInteger();

    public TestScenarioCapturingLifecyclePort() {
    }

    @Override
    public void beforeEach(TestContext testContext) {
        boolean bound = testContext.getMetadata(RequestContextController.class).isPresent();
        CONTROLLER_BOUND_AFTER_BEFORE_EACH.set(bound);
    }

    @Override
    public void afterEach(TestContext testContext) {
        AFTER_EACH_INVOCATIONS.incrementAndGet();
    }
}
