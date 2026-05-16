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
package org.os890.jawelte.tests.wiremock.scenario17;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Scenario 17 — scope sandwich. Verifies the behaviour
 * wiremock-module's {@code @Priority(75)} positioning enables:
 * a {@code @TestClassScoped} bean's {@code @PreDestroy} (driven
 * by scope-module deactivating the class scope at
 * {@code @Priority(100)}) sees the
 * {@code WireMockServer} <b>still running</b> — wiremock-module's
 * {@code afterAll} hasn't yet been reached in the LIFO
 * adapter chain.
 *
 * <p>The numeric priority value isn't asserted; the
 * <em>behaviour</em> the value enables is.
 */
class Scenario17Test {

    @Test
    void preDestroyObservesServerStillRunning() {
        Scenario17Recorder.PRE_DESTROY_INVOKED.set(false);
        Scenario17Recorder.SERVER_RUNNING_AT_PRE_DESTROY.set(false);

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario17Subject.class))
                .execute();

        assertThat(Scenario17Recorder.PRE_DESTROY_INVOKED)
                .as("scope-module deactivated @TestClassScoped and invoked the bean's @PreDestroy")
                .isTrue();
        assertThat(Scenario17Recorder.SERVER_RUNNING_AT_PRE_DESTROY)
                .as("wiremock-module hadn't stopped the server yet when scope @PreDestroy ran")
                .isTrue();
    }
}
