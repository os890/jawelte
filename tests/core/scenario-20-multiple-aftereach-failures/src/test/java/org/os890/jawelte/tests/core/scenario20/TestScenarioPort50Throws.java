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
package org.os890.jawelte.tests.core.scenario20;

import jakarta.annotation.Priority;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;

@Priority(50)
public class TestScenarioPort50Throws implements TestModuleLifecyclePort {

    public TestScenarioPort50Throws() {
    }

    @Override
    public void afterEach(TestContext testContext) {
        throw new IllegalStateException("AAA afterEach failure from priority 50");
    }
}
