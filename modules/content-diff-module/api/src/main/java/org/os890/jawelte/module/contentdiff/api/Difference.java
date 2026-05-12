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
package org.os890.jawelte.module.contentdiff.api;

/**
 * Single structural difference reported by a {@link
 * org.os890.jawelte.module.contentdiff.api.port.DiffEngine}.
 *
 * <p>Returned by every engine implementation; the
 * {@link org.os890.jawelte.module.contentdiff.api.port.DiffEngine#diff(String, String, DiffOptions)}
 * method returns an immutable {@code List<Difference>}.
 *
 * <h2>Field semantics</h2>
 *
 * <ul>
 *   <li>{@code path} — JSON-path (e.g. {@code $.items[2].id}) or
 *       XPath (e.g. {@code /orders/order[3]/id}) of the differing
 *       leaf. The engine that produced this record chooses the
 *       path dialect for its content type.</li>
 *   <li>{@code expected} — formatted leaf value from the expected
 *       document, or the sentinel string {@value MISSING} when the
 *       leaf is absent on that side.</li>
 *   <li>{@code actual} — formatted leaf value from the actual
 *       document, or the sentinel string {@value MISSING} when the
 *       leaf is absent on that side.</li>
 *   <li>{@code expectedLineNumber} — 1-based line in the expected
 *       document pointing at the difference; {@code 0} when the
 *       engine cannot determine a line (e.g. inline string passed
 *       to {@code expectedContent(...)} that the engine still
 *       parsed positionally — the line refers to the document the
 *       engine saw).</li>
 * </ul>
 *
 * <p>Records are deeply immutable and trivially equal-by-value.
 *
 * @param path               JSON-path or XPath of the difference
 * @param expected           formatted expected leaf value or {@value MISSING}
 * @param actual             formatted actual leaf value or {@value MISSING}
 * @param expectedLineNumber 1-based line in the expected document
 */
public record Difference(
        String path,
        String expected,
        String actual,
        int expectedLineNumber) {

    /**
     * Sentinel string used in {@link #expected()} / {@link #actual()}
     * when the leaf is absent on that side of the comparison.
     * Distinguishes "missing" from JSON {@code null} (which serialises
     * to the literal {@code "null"}).
     */
    public static final String MISSING = "<missing>";
}
