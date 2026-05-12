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
package org.os890.jawelte.tests.contentdiff.scenario38;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;

class Scenario38Test {

    @Test
    void jsonSpecificArrayIndexIgnoreMatchesOnlyThatIndex() {
        // $.items[1] is the dialect's specific-index shape; only the
        // second element should be ignored.
        String expected = "{\"items\":[1,2,3]}";
        String actual = "{\"items\":[1,99,3]}";
        ContentDiff.forJson(actual)
                .expectedContent(expected)
                .ignoring("$.items[1]")
                .assertEquals();
    }

    @Test
    void xmlExplicitPredicateIgnoreMatchesOnlyThatSibling() {
        // /root/item[2] is the dialect's explicit-predicate shape;
        // only the second <item> should be ignored.
        String expected = "<root><item>1</item><item>2</item><item>3</item></root>";
        String actual = "<root><item>1</item><item>99</item><item>3</item></root>";
        ContentDiff.forXml(actual)
                .expectedContent(expected)
                .ignoring("/root/item[2]")
                .assertEquals();
    }

    @Test
    void unclosedBracketInJsonPatternThrows() {
        assertThatThrownBy(() ->
                ContentDiff.forJson("{}")
                        .expectedContent("{}")
                        .ignoring("$.items[")
                        .assertEquals())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unclosed");
    }

    @Test
    void unclosedPredicateInXmlPatternThrows() {
        assertThatThrownBy(() ->
                ContentDiff.forXml("<r/>")
                        .expectedContent("<r/>")
                        .ignoring("/r/x[")
                        .assertEquals())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unclosed");
    }

    @Test
    void emptyStepNameInXmlPatternThrows() {
        // "/r/" ends with no step name after the trailing slash, so the
        // dialect parser throws.
        assertThatThrownBy(() ->
                ContentDiff.forXml("<r/>")
                        .expectedContent("<r/>")
                        .ignoring("/r/")
                        .assertEquals())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonSlashStartingXmlPatternMatchesNothing() {
        // A pattern that doesn't start with `/` compiles to a regex
        // that matches nothing — the api contract says mixing pattern
        // syntaxes is a silent no-op. So the diff still surfaces
        // because the ignore pattern doesn't match the diff's path.
        assertThatThrownBy(() ->
                ContentDiff.forXml("<r><x>2</x></r>")
                        .expectedContent("<r><x>1</x></r>")
                        .ignoring("$.x")
                        .assertEquals())
                .isInstanceOf(AssertionError.class);
    }
}
