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
package org.os890.jawelte.tests.contentdiff.scenario33;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;

/**
 * Pins the contract of the alternative (glob) {@code
 * JsonPatternDialect}: the no-wildcard subset of glob patterns
 * compiles to a regex that matches the same concrete paths the
 * default JSONPath dialect would. Migrating fixtures from a
 * project written for the glob grammar means translating only
 * the wildcard shapes — exact paths keep their spelling.
 */
class Scenario33Test {

    @Test
    void exactTopLevelFieldIgnoredUnderGlob() {
        String expected = "{\"id\":1,\"name\":\"Alice\"}";
        String actual = "{\"id\":2,\"name\":\"Alice\"}";
        ContentDiff.forJson(actual)
                .expectedContent(expected)
                .ignoring("$.id")
                .assertEquals();
    }

    @Test
    void exactNestedFieldIgnoredUnderGlob() {
        String expected = "{\"user\":{\"id\":1,\"name\":\"Alice\"}}";
        String actual = "{\"user\":{\"id\":99,\"name\":\"Alice\"}}";
        ContentDiff.forJson(actual)
                .expectedContent(expected)
                .ignoring("$.user.id")
                .assertEquals();
    }

    @Test
    void exactIndexedFieldIgnoredUnderGlob() {
        String expected = "{\"users\":[{\"id\":1,\"name\":\"Alice\"}]}";
        String actual = "{\"users\":[{\"id\":99,\"name\":\"Alice\"}]}";
        ContentDiff.forJson(actual)
                .expectedContent(expected)
                .ignoring("$.users[0].id")
                .assertEquals();
    }
}
