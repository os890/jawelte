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
package org.os890.jawelte.tests.contentdiff.scenario28;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;

class Scenario28Test {

    @Test
    void fiftyDifferencesAllReported() {
        StringBuilder expected = new StringBuilder("{");
        StringBuilder actual = new StringBuilder("{");
        for (int index = 0; index < 50; index++) {
            if (index > 0) {
                expected.append(",");
                actual.append(",");
            }
            expected.append("\"f").append(index).append("\":").append(index);
            actual.append("\"f").append(index).append("\":").append(index + 100);
        }
        expected.append("}");
        actual.append("}");

        Throwable failure = catchThrowable(() ->
                ContentDiff.forJson(actual.toString())
                        .expectedContent(expected.toString())
                        .assertEquals());
        assertThat(failure).isInstanceOf(AssertionError.class);
        String firstLine = failure.getMessage().split("\\R", 2)[0];
        assertThat(firstLine).isEqualTo("JSON diff found 50 difference(s):");
        for (int index = 0; index < 50; index++) {
            assertThat(failure.getMessage()).contains("$.f" + index);
        }
    }
}
