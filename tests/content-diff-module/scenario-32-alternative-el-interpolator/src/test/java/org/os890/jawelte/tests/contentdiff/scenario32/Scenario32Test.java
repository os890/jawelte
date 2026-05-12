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
package org.os890.jawelte.tests.contentdiff.scenario32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;

class Scenario32Test {

    @Test
    void alternativeInterpolatorBypassesElEvaluation() {
        // Under the default JakartaELInterpolator the same call would
        // resolve ${name} to "Alice" and the diff would pass. The
        // scenario-scoped TestScenarioPassThroughInterpolator (lower
        // @Priority) wins through the SPI lookup, returns the
        // template verbatim, and the diff surfaces the literal
        // ${name} token as a string mismatch against the actual
        // value "Alice".
        String template = "{\"name\":\"${name}\"}";
        String actual = "{\"name\":\"Alice\"}";

        assertThatThrownBy(() ->
                ContentDiff.forJson(actual)
                        .expectedContent(template)
                        .withValues(Map.of("name", "Alice"))
                        .assertEquals())
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains("$.name")
                        .contains("${name}")
                        .contains("Alice"));
    }
}
