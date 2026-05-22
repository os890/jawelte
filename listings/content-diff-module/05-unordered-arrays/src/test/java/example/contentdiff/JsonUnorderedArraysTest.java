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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;

class JsonUnorderedArraysTest {

    @Test
    void taggedArrayComparesAsMultiset() {
        // Same three tags, different order — unorderedArrays($.tags) tells
        // the diff to compare as a multiset, so the assertion passes.
        String actual   = "{\"name\":\"Alice\",\"tags\":[\"vip\",\"new\",\"trial\"]}";
        String expected = "{\"name\":\"Alice\",\"tags\":[\"new\",\"trial\",\"vip\"]}";

        ContentDiff.forJson(actual)
                .unorderedArrays("$.tags")
                .expectedContent(expected)
                .assertEquals();
    }

    @Test
    void arrayWithoutUnorderedOptInStaysIndexWise() {
        // The same payload without .unorderedArrays(...) fails — the
        // default is order-sensitive at every array level.
        String actual   = "{\"name\":\"Alice\",\"tags\":[\"vip\",\"new\",\"trial\"]}";
        String expected = "{\"name\":\"Alice\",\"tags\":[\"new\",\"trial\",\"vip\"]}";

        assertThatThrownBy(() ->
                ContentDiff.forJson(actual)
                        .expectedContent(expected)
                        .assertEquals())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("tags");
    }
}
