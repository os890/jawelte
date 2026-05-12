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

import java.util.List;

import org.os890.jawelte.module.contentdiff.api.port.DiffEngine;

/**
 * Single-use fluent builder for JSON content diffs. Returned by
 * {@link ContentDiff#forJson(String)}; not reusable across two
 * assertions and not thread-safe.
 *
 * <p>Accepts JSON-path-style ignore patterns:
 * <ul>
 *   <li>{@code $.field} — skip a top-level field;</li>
 *   <li>{@code $.items[*].field} — skip a field in every element
 *       of an array;</li>
 *   <li>{@code $..field} — recursive descent: skip the field at any
 *       depth.</li>
 * </ul>
 *
 * <p>{@link #unorderedArrays(String...)} selects the specific
 * array paths whose elements should be compared as a multiset
 * (count-sensitive, order-independent). Arrays whose path matches
 * none of the configured patterns stay index-wise. The check is
 * applied at every level, so a pattern matching a nested array
 * (e.g. {@code $.outer[*]} for the inner arrays of
 * {@code [[1,2],[3,4]]}) takes effect on that level too.
 */
public class JsonBuilder extends AbstractContentBuilder<JsonBuilder> {

    JsonBuilder(
            DiffEngine engine,
            String actualContent,
            List<String> ignoreDefaults,
            List<String> unorderedDefaults) {
        super(engine, actualContent, ignoreDefaults, unorderedDefaults);
    }

    /**
     * Declare one or more array paths whose elements should be
     * compared with multiset semantics. Cumulative — multiple
     * calls union the set. Empty varargs invocation is a no-op
     * (kept for chaining ergonomics; absence of any pattern leaves
     * every array index-wise).
     *
     * <p>Patterns follow the JSON-path dialect used by
     * {@link #ignoring(String...)}: {@code $.items} matches the
     * array literal at that path, {@code $.items[*]} matches every
     * element of {@code $.items} (so a nested array inside
     * {@code $.items} also matches), and {@code $..tags} matches
     * an array named {@code tags} at any depth.
     *
     * @param paths one or more JSON-path patterns identifying
     *              arrays the diff should treat as multisets
     * @return this builder for chaining
     */
    public JsonBuilder unorderedArrays(String... paths) {
        for (String pattern : paths) {
            unorderedArrayPaths().add(pattern);
        }
        return this;
    }

    @Override
    String formatName() {
        return ContentDiff.JSON_FORMAT_NAME;
    }
}
