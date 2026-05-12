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
package org.os890.jawelte.tests.contentdiff.scenario41;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;

class Scenario41Test {

    @Test
    void unorderedMatchOfComplexObjectsExercisesStructuralEquality() {
        // Each array element is an object that itself contains a
        // nested array. Multiset match at $ walks objects through
        // structurallyEqual's object branch (visibleFields collects
        // the field set; child arrays compare by index-wise
        // recursion).
        String expected = "{\"users\":["
                + "{\"id\":1,\"tags\":[\"a\",\"b\"]},"
                + "{\"id\":2,\"tags\":[\"c\"]}"
                + "]}";
        String actual = "{\"users\":["
                + "{\"id\":2,\"tags\":[\"c\"]},"
                + "{\"id\":1,\"tags\":[\"a\",\"b\"]}"
                + "]}";
        ContentDiff.forJson(actual)
                .expectedContent(expected)
                .unorderedArrays("$.users")
                .assertEquals();
    }

    @Test
    void unorderedMatchWithMixedTypesExercisesTypeMismatchInStructuralEquality() {
        // First expected element is an object; actual[0] is a string.
        // Multiset trial: structurallyEqual({"id":1}, "literal") hits
        // the type-mismatch return-false branch before moving on to
        // try {"id":1} against actual[1] which matches.
        String expected = "[{\"id\":1},\"literal\"]";
        String actual = "[\"literal\",{\"id\":1}]";
        ContentDiff.forJson(actual)
                .expectedContent(expected)
                .unorderedArrays("$")
                .assertEquals();
    }

    @Test
    void unorderedMatchWithDifferingNestedFieldFailsStructuralEquality() {
        // Two objects with the same shape but different tags arrays.
        // structurallyEqual walks into the tags array; index-wise
        // compare fails on the second element ("b" vs "z"); the
        // overall match for that pair returns false. With no other
        // element to match against on the actual side, the diff
        // surfaces as an extra+missing pair.
        String expected = "{\"items\":[{\"tags\":[\"a\",\"b\"]}]}";
        String actual = "{\"items\":[{\"tags\":[\"a\",\"z\"]}]}";
        assertThatThrownBy(() ->
                ContentDiff.forJson(actual)
                        .expectedContent(expected)
                        .unorderedArrays("$.items")
                        .assertEquals())
                .isInstanceOf(AssertionError.class);
    }
}
