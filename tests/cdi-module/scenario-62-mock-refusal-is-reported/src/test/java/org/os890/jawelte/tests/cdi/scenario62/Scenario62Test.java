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
package org.os890.jawelte.tests.cdi.scenario62;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.cdi.api.port.MockFactory;
import org.os890.jawelte.module.cdi.impl.adapter.mock.MockitoMockFactory;

/**
 * A refusal from the mocking library must not be invisible.
 *
 * <p>{@code null} is a legitimate answer in the {@link MockFactory}
 * contract — "not mockable, skip it" — and the right answer for a type
 * that genuinely cannot be instrumented. Given silently when the
 * <em>library</em> is unusable, it means every type is refused,
 * auto-mocking is off for the whole deployment, and nothing anywhere
 * says so. That cost three rounds of misdiagnosis on #124.
 *
 * <p>The report itself is a log line, which a test should not assert on.
 * What is assertable is the contract around it, and that is what this
 * scenario pins: the default answer stays {@code null}, and a suite that
 * wants auto-mocking to be load-bearing can turn a refusal into a
 * failure that names the type.
 *
 * <p>This scenario ships {@code mock-maker-subclass}, so Mockito is
 * genuinely usable here — without it the inline mock maker cannot
 * self-attach its agent under this JDK and <em>every</em> type is
 * refused, which is the environmental case rather than the type-specific
 * one. {@code String} is final, so the subclass mock maker refuses it
 * while working normally for everything else.
 */
class Scenario62Test {

    private final MockFactory factory = new MockitoMockFactory();

    @AfterEach
    void clearStrictMode() {
        System.clearProperty(MockitoMockFactory.FAIL_ON_REFUSAL_KEY);
    }

    @Test
    void aMockableTypeStillGetsAMock() {
        assertThat(factory.create(Runnable.class))
                .as("the library has to be usable here, or a refusal below would be "
                        + "environmental and this scenario would prove nothing")
                .isNotNull();
    }

    @Test
    void aRefusedTypeAnswersNullByDefault() {
        assertThat(factory.create(String.class))
                .as("null stays the contract's answer for a type that cannot be mocked")
                .isNull();
    }

    @Test
    void aRefusedTypeFailsWhenTheSuiteAsksItTo() {
        System.setProperty(MockitoMockFactory.FAIL_ON_REFUSAL_KEY, "true");

        assertThatThrownBy(() -> factory.create(String.class))
                .as("a suite that wants auto-mocking to be load-bearing must be able to "
                        + "make a silent skip fail instead")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("java.lang.String")
                .hasMessageContaining(MockitoMockFactory.FAIL_ON_REFUSAL_KEY);
    }
}
