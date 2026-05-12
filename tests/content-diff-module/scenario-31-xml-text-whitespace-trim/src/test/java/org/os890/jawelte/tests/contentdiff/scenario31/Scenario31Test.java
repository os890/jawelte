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
package org.os890.jawelte.tests.contentdiff.scenario31;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;

class Scenario31Test {

    @Test
    void leafTextWithSurroundingWhitespaceMatchesTrimmedExpected() {
        // Expected text has no surrounding whitespace; actual has
        // both leading and trailing whitespace (a common artefact
        // of XML serialisers that pretty-print or pad CDATA).
        // The XML engine trims leaf-element text before comparing,
        // so the two payloads are equivalent.
        String expected = "<note><body>Alice's reply</body></note>";
        String actual = "<note><body>   Alice's reply\n   </body></note>";

        ContentDiff.forXml(actual)
                .expectedContent(expected)
                .assertEquals();
    }
}
