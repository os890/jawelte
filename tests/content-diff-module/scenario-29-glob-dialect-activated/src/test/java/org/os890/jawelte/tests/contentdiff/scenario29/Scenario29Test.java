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
package org.os890.jawelte.tests.contentdiff.scenario29;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;

class Scenario29Test {

    @Test
    void globDialectMatchesFieldAtAnyDepth() {
        // Under the default JSONPath dialect, "$.*.createdAt" matches
        // nothing (no segment wildcard in that grammar). Under the
        // glob dialect activated for this scenario via
        // META-INF/services, "*" stands for zero or more path segments
        // and array indices are stripped during matching, so the
        // pattern catches createdAt at any depth — including inside
        // array elements.
        String expected = "{"
                + "\"createdAt\":\"X\","
                + "\"user\":{\"createdAt\":\"Y\"},"
                + "\"events\":[{\"createdAt\":\"Z\"}]"
                + "}";
        String actual = "{"
                + "\"createdAt\":\"A\","
                + "\"user\":{\"createdAt\":\"B\"},"
                + "\"events\":[{\"createdAt\":\"C\"}]"
                + "}";

        ContentDiff.forJson(actual)
                .expectedContent(expected)
                .ignoring("$.*.createdAt")
                .assertEquals();
    }
}
