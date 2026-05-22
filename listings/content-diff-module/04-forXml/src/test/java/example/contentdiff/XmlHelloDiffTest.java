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
package example.contentdiff;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;

class XmlHelloDiffTest {

    @Test
    void semanticallyEqualXmlPassesSilently() {
        // Same element shape, attribute order differs — the XML engine
        // compares attribute sets, not byte streams.
        String actual   = "<customer id=\"1\" name=\"Alice\"/>";
        String expected = "<customer name=\"Alice\" id=\"1\"/>";

        ContentDiff.forXml(actual)
                .expectedContent(expected)
                .assertEquals();
    }

    @Test
    void deepXmlStructuresCompareByMeaning() {
        // Nested elements with the same shape — child order matters for
        // the default XML dialect, but inner attribute order does not.
        String actual = "<order><line id=\"1\" qty=\"3\"/><line id=\"2\" qty=\"1\"/></order>";
        String expected = "<order><line qty=\"3\" id=\"1\"/><line qty=\"1\" id=\"2\"/></order>";

        ContentDiff.forXml(actual)
                .expectedContent(expected)
                .assertEquals();
    }
}
