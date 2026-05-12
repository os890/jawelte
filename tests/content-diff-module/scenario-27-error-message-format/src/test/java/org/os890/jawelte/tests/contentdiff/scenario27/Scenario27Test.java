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
package org.os890.jawelte.tests.contentdiff.scenario27;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;

class Scenario27Test {

    @Test
    void errorMessageFormatMatchesDocumentedShape() {
        String expected = "{\n"
                + "  \"a\": 1\n"
                + "}";
        String actual = "{\"a\":2}";
        Throwable failure = catchThrowable(() ->
                ContentDiff.forJson(actual).expectedContent(expected).assertEquals());
        assertThat(failure).isInstanceOf(AssertionError.class);
        String firstLine = failure.getMessage().split("\\R", 2)[0];
        assertThat(firstLine).isEqualTo("JSON diff found 1 difference(s):");
        assertThat(failure.getMessage())
                .contains("$.a: expected=\"1\" actual=\"2\" (expected file line 2)");
    }
}
