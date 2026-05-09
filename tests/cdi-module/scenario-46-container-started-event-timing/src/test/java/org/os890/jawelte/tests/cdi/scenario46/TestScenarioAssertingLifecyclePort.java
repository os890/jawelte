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
package org.os890.jawelte.tests.cdi.scenario46;

import java.util.concurrent.atomic.AtomicBoolean;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;

public class TestScenarioAssertingLifecyclePort implements TestModuleLifecyclePort {

    public static final AtomicBoolean SAW_CONTAINER_STARTED_BEFORE_BEFORE_ALL = new AtomicBoolean();

    public TestScenarioAssertingLifecyclePort() {
    }

    @Override
    public void beforeAll(TestContext testContext) {
        SAW_CONTAINER_STARTED_BEFORE_BEFORE_ALL.set(StartupListener.RECEIVED_CONTAINER_STARTED.get());
    }
}
