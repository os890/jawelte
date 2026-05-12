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
package org.os890.jawelte.tests.contentdiff.scenario40;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;

class Scenario40Test {

    @Test
    void unbalancedDollarBraceLeavesTemplateRemainderVerbatim() {
        // Top-level JSON string literal that contains `${` and ends
        // before any matching `}`. The interpolator finds the `${`,
        // can't locate a closing brace, copies the rest of the
        // template into the output verbatim, and breaks out of the
        // substitution loop. The diff then runs on the
        // un-interpolated template — which, against an identical
        // actual, matches with no differences.
        String document = "\"prefix ${unclosed-rest-of-doc\"";
        ContentDiff.forJson(document)
                .expectedContent(document)
                .assertEquals();
    }
}
