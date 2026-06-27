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
package org.os890.jawelte.tests.contentdiff.scenario42;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;

/**
 * A {@code ${...}} expression that legitimately contains a {@code '}'}
 * must be passed to the EL parser intact. The brace can sit inside an
 * EL string literal (here via {@code String.concat('}')}) or inside a
 * nested map literal ({@code {'a':1}}). A naive "first closing brace
 * wins" scan truncates such an expression and hands the EL parser a
 * malformed fragment, which throws at parse time; the brace-aware scan
 * keeps the whole expression together.
 */
class Scenario42Test {

    @Test
    void stringLiteralContainingClosingBraceIsNotTruncated() {
        // The '}' lives inside the EL string literal argument to concat.
        // Interpolates to the JSON string "x}".
        String expected = "{\"v\":\"${name.concat('}')}\"}";
        String actual = "{\"v\":\"x}\"}";
        ContentDiff.forJson(actual)
                .expectedContent(expected)
                .withValues(Map.of("name", "x"))
                .assertEquals();
    }

    @Test
    void nestedMapLiteralBracesAreNotTruncated() {
        // The inner '}' closes the EL map literal, not the expression.
        // {'a':1}['a'] evaluates to 1.
        String expected = "{\"v\":${ {'a':1}['a'] }}";
        String actual = "{\"v\":1}";
        ContentDiff.forJson(actual)
                .expectedContent(expected)
                .assertEquals();
    }
}
