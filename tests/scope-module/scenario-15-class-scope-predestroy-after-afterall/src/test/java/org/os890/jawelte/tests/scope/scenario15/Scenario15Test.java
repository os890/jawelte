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
package org.os890.jawelte.tests.scope.scenario15;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

class Scenario15Test {

    @Test
    void preDestroyFiresAfterAfterAll() {
        Scenario15Subject.EVENTS.clear();

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario15Subject.class))
                .execute();

        // Subject's @AfterAll appends "AFTER_ALL" first; then the
        // JUnit Extension's afterAll runs the lifecycle adapter chain,
        // which calls TestClassScopedContext.deactivate() ->
        // store.destroyAll() -> Contextual.destroy(...) -> @PreDestroy
        // appends "PRE_DESTROY".
        assertThat(Scenario15Subject.EVENTS).containsExactly("AFTER_ALL", "PRE_DESTROY");
    }
}
