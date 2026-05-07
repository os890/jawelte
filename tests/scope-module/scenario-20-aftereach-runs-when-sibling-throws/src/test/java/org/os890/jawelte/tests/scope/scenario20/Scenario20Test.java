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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

class Scenario20Test {

    @Test
    void scopeModuleAfterEachRunsEvenWhenSiblingPort150Threw() {
        Scenario20Subject.RECORDED.clear();

        var execution = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario20Subject.class))
                .execute();

        // The test failed because Port150Throws.beforeEach threw.
        assertThat(execution.testEvents().failed().count()).isEqualTo(1);

        // Port50Probe.afterEach ran AFTER scope-module's afterEach (LIFO),
        // and observed the method-scope store nulled by scope-module's
        // unconditional deactivate.
        assertThat(Scenario20Subject.RECORDED)
                .contains("BEFORE_50", "AFTER_50_SCOPE_DEACTIVATED");
    }
}
