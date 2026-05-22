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

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;

/**
 * The JVM-wide JSON ignore default (`$..audit.timestamp`) ships in
 * `src/test/resources/META-INF/microprofile-config.properties`. The
 * test method does NOT call `.ignoring(...)` explicitly — the diff
 * still passes because the configured default is prepended to the
 * (empty) caller list before the engine runs.
 *
 * <p>Confidence in the contract: MP Config defaults are additive, not
 * overrideable from the call site. A second test method shows that
 * adding a caller-side `.ignoring("$.note")` on top still works — the
 * union of both pattern sources is what the diff skips.
 */
class JsonMpConfigDefaultsTest {

    @Test
    void mpConfigDefaultIgnoresAuditTimestampWithoutAnyBuilderCall() {
        // Both payloads carry an audit.timestamp; their values differ.
        // No .ignoring(...) call on the builder — yet the diff passes
        // because the MP Config default pattern $..audit.timestamp
        // skips that field at any depth.
        String actual = "{\"name\":\"Alice\",\"audit\":{\"timestamp\":\"2026-05-20T10:00:00Z\"}}";
        String expected = "{\"name\":\"Alice\",\"audit\":{\"timestamp\":\"1970-01-01T00:00:00Z\"}}";

        ContentDiff.forJson(actual)
                .expectedContent(expected)
                .assertEquals();
    }

    @Test
    void inlineIgnoringIsMergedWithMpConfigDefaults() {
        // The actual payload differs on `note` AND on audit.timestamp.
        // The MP Config default skips audit.timestamp; the inline
        // .ignoring("$.note") covers the second difference. Union of
        // both sources means the diff has nothing left to flag.
        String actual = "{\"name\":\"Alice\",\"note\":\"draft\",\"audit\":{\"timestamp\":\"2026-05-20T10:00:00Z\"}}";
        String expected = "{\"name\":\"Alice\",\"note\":\"final\",\"audit\":{\"timestamp\":\"1970-01-01T00:00:00Z\"}}";

        ContentDiff.forJson(actual)
                .ignoring("$.note")
                .expectedContent(expected)
                .assertEquals();
    }
}
