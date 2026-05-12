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
package org.os890.jawelte.tests.contentdiff.scenario39;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;

class Scenario39Test {

    @Test
    void singleSegmentWildcardMatchesOneLevelDeep() {
        // Under the glob dialect, `/*/timestamp` matches an element
        // exactly one level under the root, so the inner-wrapped
        // <timestamp> at two levels deep is NOT matched.
        String expected = "<root>"
                + "<timestamp>2026-01-01</timestamp>"
                + "<wrapper><timestamp>2026-02-02</timestamp></wrapper>"
                + "</root>";
        String actual = "<root>"
                + "<timestamp>X</timestamp>"
                + "<wrapper><timestamp>Y</timestamp></wrapper>"
                + "</root>";
        // The top-level <timestamp> is at depth 1 — matched and
        // ignored. The wrapped one at depth 2 is NOT matched, so its
        // mismatch (Y vs 2026-02-02) still surfaces.
        assertThatThrownBy(() ->
                ContentDiff.forXml(actual)
                        .expectedContent(expected)
                        .ignoring("/*/timestamp")
                        .assertEquals())
                .satisfies(failure -> {
                    String message = failure.getMessage();
                    org.assertj.core.api.Assertions.assertThat(message)
                            .contains("/root/wrapper")
                            .contains("timestamp");
                });
    }

    @Test
    void predicateInsideExplicitSegmentMatchesOnlyThatIndex() {
        // Under the glob dialect, `/root/item[2]` is a literal-with-
        // predicate step; only the second <item> is ignored.
        String expected = "<root><item>1</item><item>2</item><item>3</item></root>";
        String actual = "<root><item>1</item><item>99</item><item>3</item></root>";
        ContentDiff.forXml(actual)
                .expectedContent(expected)
                .ignoring("/root/item[2]")
                .assertEquals();
    }

    @Test
    void nonSlashStartingPatternMatchesNothing() {
        // Glob dialect's MATCHES_NOTHING path: a pattern that doesn't
        // start with `/` compiles to a regex matching no concrete path.
        assertThatThrownBy(() ->
                ContentDiff.forXml("<r><x>2</x></r>")
                        .expectedContent("<r><x>1</x></r>")
                        .ignoring("bogus")
                        .assertEquals())
                .isInstanceOf(AssertionError.class);
    }
}
