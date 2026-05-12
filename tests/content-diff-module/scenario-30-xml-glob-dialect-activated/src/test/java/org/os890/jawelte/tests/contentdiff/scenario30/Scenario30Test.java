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
package org.os890.jawelte.tests.contentdiff.scenario30;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;

class Scenario30Test {

    @Test
    void xmlGlobDialectMatchesElementAtAnyDepth() {
        // Under the default XPath dialect, "/**/timestamp" doesn't
        // parse (the XPath axis for "any depth" is "//"). The glob
        // dialect activated for this scenario via META-INF/services
        // accepts "/**/<name>" and treats it as recursive descent,
        // so both top-level and nested <timestamp> elements get
        // ignored.
        String expected = "<root>"
                + "<timestamp>2026-01-01</timestamp>"
                + "<wrapper><timestamp>2026-02-02</timestamp></wrapper>"
                + "</root>";
        String actual = "<root>"
                + "<timestamp>X</timestamp>"
                + "<wrapper><timestamp>Y</timestamp></wrapper>"
                + "</root>";

        ContentDiff.forXml(actual)
                .expectedContent(expected)
                .ignoring("/**/timestamp")
                .assertEquals();
    }
}
