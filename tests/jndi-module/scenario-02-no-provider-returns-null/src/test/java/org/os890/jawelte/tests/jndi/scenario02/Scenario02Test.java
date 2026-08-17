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

import javax.naming.Context;

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
 *
 * <p>The last test covers the other half of answering {@code null}:
 * leaving the JVM exactly as it was found.
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

    /**
     * Reporting absence must also mean leaving no trace of the attempt.
     *
     * <p>The adapter points {@code java.naming.factory.initial} at
     * xbean's factory as part of installing it. If it did that before
     * finding out whether xbean is there — which is what jta-module's
     * bootstrap used to do — a JVM whose only naming provider is its
     * container's would be left with the property naming a class it
     * cannot load, and a plain {@code new InitialContext()} would fail
     * <em>because</em> jndi-module had been asked a question it answered
     * with "nothing here".
     */
    @Test
    void aFailedResolutionLeavesTheNamingPropertiesAlone() {
        TestContext.loadService(JndiContextProvider.class).writableRoot();

        assertThat(System.getProperty(Context.INITIAL_CONTEXT_FACTORY))
                .as("no provider was installed, so nothing may claim to be one")
                .isNull();
        assertThat(System.getProperty(Context.URL_PKG_PREFIXES)).isNull();
    }
}
