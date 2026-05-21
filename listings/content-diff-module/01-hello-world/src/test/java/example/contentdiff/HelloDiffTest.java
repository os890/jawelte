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

class HelloDiffTest {

    @Test
    void identicalJsonPassesSilently() {
        String document = "{\"name\":\"Alice\",\"age\":30}";
        ContentDiff.forJson(document)
                .expectedContent(document)
                .assertEquals();
    }

    @Test
    void mismatchRaisesAssertionErrorThatNamesTheField() {
        String actual = "{\"name\":\"Alice\",\"age\":30}";
        String expected = "{\"name\":\"Alice\",\"age\":31}";

        assertThatThrownBy(() ->
                ContentDiff.forJson(actual)
                        .expectedContent(expected)
                        .assertEquals())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("age");
    }

    @Test
    void ignoringPatternSkipsTheNoisyField() {
        String actual = "{\"name\":\"Alice\",\"audit\":{\"timestamp\":\"2026-05-20T10:00:00Z\"}}";
        String expected = "{\"name\":\"Alice\",\"audit\":{\"timestamp\":\"1970-01-01T00:00:00Z\"}}";

        ContentDiff.forJson(actual)
                .ignoring("$.audit.timestamp")
                .expectedContent(expected)
                .assertEquals();
    }
}
