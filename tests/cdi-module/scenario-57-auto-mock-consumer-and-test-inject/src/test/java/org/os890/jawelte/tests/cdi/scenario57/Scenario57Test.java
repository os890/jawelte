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
package org.os890.jawelte.tests.cdi.scenario57;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Regression: a CDI bean's {@code @Inject} of an unsatisfied
 * dependency AND the test class's own {@code @Inject} of that same
 * unsatisfied dependency must share a single auto-mock — not two.
 *
 * <p>Before the fix, the IpKey collected via
 * {@code ProcessInjectionPoint} for the production bean carried
 * {@code @Default} (CDI normalises unqualified IPs to that
 * qualifier), while the IpKey collected via the manual
 * {@code addTestClassInjectionPoints} walk of the test class's
 * fields carried an empty qualifier set. The two IpKeys were not
 * equal, the dedup set kept both, and the synthetic-bean loop
 * registered two auto-mocks for the same {@code @Default Translator}
 * IP — causing CDI bean validation to raise
 * {@code AmbiguousResolutionException} on container start.
 *
 * <p>The fix normalises an empty qualifier set in
 * {@code addTestClassInjectionPoints} to
 * {@code {@Default.Literal.INSTANCE}} so both code paths produce
 * equivalent IpKeys for unqualified IPs.
 */
@EnableTestBeans
class Scenario57Test {

    @Inject
    Greeter greeter;

    @Inject
    Translator translator;

    @Test
    void productionConsumerAndTestInjectShareOneAutoMock() {
        when(translator.translate("jawelte")).thenReturn("JAWELTE");

        assertThat(greeter.greet("jawelte")).isEqualTo("hello, JAWELTE!");
    }
}
