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
package org.os890.jawelte.tests.contentdiff.scenario34;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;

class Scenario34Test {

    @Test
    void attributeWithDifferentValueIsReported() {
        String expected = "<root attr=\"a\"/>";
        String actual = "<root attr=\"x\"/>";
        assertThatThrownBy(() ->
                ContentDiff.forXml(actual).expectedContent(expected).assertEquals())
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains("/root/@attr")
                        .contains("expected=\"a\"")
                        .contains("actual=\"x\""));
    }

    @Test
    void attributeMissingFromActualIsReportedWithMissingSentinel() {
        String expected = "<root x=\"1\" y=\"2\"/>";
        String actual = "<root x=\"1\"/>";
        assertThatThrownBy(() ->
                ContentDiff.forXml(actual).expectedContent(expected).assertEquals())
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains("/root/@y")
                        .contains("expected=\"2\"")
                        .contains("actual=\"<missing>\""));
    }

    @Test
    void attributeExtraInActualIsReportedWithMissingSentinelOnExpected() {
        String expected = "<root x=\"1\"/>";
        String actual = "<root x=\"1\" z=\"3\"/>";
        assertThatThrownBy(() ->
                ContentDiff.forXml(actual).expectedContent(expected).assertEquals())
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains("/root/@z")
                        .contains("expected=\"<missing>\"")
                        .contains("actual=\"3\""));
    }
}
