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
package org.os890.jawelte.tests.contentdiff.scenario20;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;

class Scenario20Test {

    @Test
    void elInvokesMethodOnBoundObject() {
        String expected = "{\"v\":${calc.doubled(5)}}";
        String actual = "{\"v\":10}";
        ContentDiff.forJson(actual)
                .expectedContent(expected)
                .withValues(Map.of("calc", new Calculator()))
                .assertEquals();
    }

    public static class Calculator {
        public int doubled(int input) {
            return input * 2;
        }
    }
}
