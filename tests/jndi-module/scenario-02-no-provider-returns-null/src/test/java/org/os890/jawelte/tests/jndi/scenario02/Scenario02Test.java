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
package org.os890.jawelte.tests.jndi.scenario02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jndi.api.port.JndiContextProvider;

/**
 * This scenario's pom omits {@code xbean-naming}, so there is no naming
 * implementation in the JVM.
 *
 * <p>{@code null} is the documented answer for that, and it is
 * deliberately not an exception: consumers disagree about whether
 * missing naming is fatal. jta-module treats it as an error, because
 * the vendor integrations resolve their artifacts by name and nothing
 * will work without them; a consumer that only publishes names as a
 * convenience, and whose real path is injection, carries on unaffected.
 * A port that threw would take that decision away from both.
 */
class Scenario02Test {

    @Test
    void theProviderItselfIsStillResolvable() {
        assertThat(TestContext.loadService(JndiContextProvider.class))
                .as("the adapter ships with the module; only the naming implementation is missing")
                .isNotNull();
    }

    @Test
    void writableRootReturnsNullRatherThanThrowing() {
        JndiContextProvider provider = TestContext.loadService(JndiContextProvider.class);

        assertThatCode(provider::writableRoot).doesNotThrowAnyException();
        assertThat(provider.writableRoot())
                .as("absence has to be reportable, so each caller can decide whether it is fatal")
                .isNull();
    }

    @Test
    void repeatedCallsKeepReportingAbsenceConsistently() {
        JndiContextProvider provider = TestContext.loadService(JndiContextProvider.class);

        assertThat(provider.writableRoot()).isNull();
        assertThat(provider.writableRoot()).isNull();
    }
}
