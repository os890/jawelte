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
package org.os890.jawelte.tests.contentdiff.scenario35;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;

class Scenario35Test {

    @Test
    void missingSameNameSiblingOnActualReported() {
        String expected = "<root><item>1</item><item>2</item></root>";
        String actual = "<root><item>1</item></root>";
        assertThatThrownBy(() ->
                ContentDiff.forXml(actual).expectedContent(expected).assertEquals())
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains("/root/item[2]")
                        .contains("actual=\"<missing>\""));
    }

    @Test
    void extraSameNameSiblingOnActualReported() {
        String expected = "<root><item>1</item></root>";
        String actual = "<root><item>1</item><item>2</item></root>";
        assertThatThrownBy(() ->
                ContentDiff.forXml(actual).expectedContent(expected).assertEquals())
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains("/root/item[2]")
                        .contains("expected=\"<missing>\""));
    }

    @Test
    void summariseCoversLeafTextEmptyAndParentBranches() {
        // Expected carries three element kinds; actual has none, so the
        // diff calls summarise(...) for each missing child and each
        // summarise branch is exercised:
        //   leaf-with-text       -> the text itself
        //   empty self-closing   -> <name/>
        //   element-with-children -> <name>...</name>
        String expected = "<root>"
                + "<leaf>text-value</leaf>"
                + "<empty></empty>"
                + "<parent><inner/></parent>"
                + "</root>";
        String actual = "<root></root>";
        assertThatThrownBy(() ->
                ContentDiff.forXml(actual).expectedContent(expected).assertEquals())
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains("/root/leaf")
                        .contains("text-value")
                        .contains("/root/empty")
                        .contains("<empty/>")
                        .contains("/root/parent")
                        .contains("<parent>...</parent>"));
    }

    @Test
    void rootElementNameMismatchReportsTagDiff() {
        String expected = "<expected-root/>";
        String actual = "<actual-root/>";
        assertThatThrownBy(() ->
                ContentDiff.forXml(actual).expectedContent(expected).assertEquals())
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains("/expected-root")
                        .contains("<expected-root>")
                        .contains("<actual-root>"));
    }
}
