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
package org.os890.jawelte.tests.contentdiff.scenario37;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;

class Scenario37Test {

    @Test
    void typeMismatchObjectVsArrayReported() {
        String expected = "{\"a\":{\"k\":1}}";
        String actual = "{\"a\":[1,2]}";
        assertThatThrownBy(() ->
                ContentDiff.forJson(actual).expectedContent(expected).assertEquals())
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains("$.a"));
    }

    @Test
    void orderedArrayWithActualLongerThanExpectedSurfacesExtras() {
        String expected = "[1,2]";
        String actual = "[1,2,99]";
        assertThatThrownBy(() ->
                ContentDiff.forJson(actual).expectedContent(expected).assertEquals())
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains("$[2]")
                        .contains("expected=\"<missing>\"")
                        .contains("actual=\"99\""));
    }

    @Test
    void orderedArrayWithExpectedLongerThanActualSurfacesMissing() {
        String expected = "[1,2,3]";
        String actual = "[1,2]";
        assertThatThrownBy(() ->
                ContentDiff.forJson(actual).expectedContent(expected).assertEquals())
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains("$[2]")
                        .contains("expected=\"3\"")
                        .contains("actual=\"<missing>\""));
    }

    @Test
    void unorderedArrayExtraOnActualSurfaces() {
        // Activate per-array unordered at root. Expected has 2 elements,
        // actual has 3 — the extra element on the actual side must be
        // reported. Exercises diffArraysUnordered's "unmatched actual"
        // path.
        String expected = "[1,2]";
        String actual = "[1,2,99]";
        assertThatThrownBy(() ->
                ContentDiff.forJson(actual)
                        .expectedContent(expected)
                        .unorderedArrays("$")
                        .assertEquals())
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains("99"));
    }
}
