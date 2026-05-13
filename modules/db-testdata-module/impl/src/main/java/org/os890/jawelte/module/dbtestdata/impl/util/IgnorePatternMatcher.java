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
package org.os890.jawelte.module.dbtestdata.impl.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Decides whether a given ({@code tableName}, {@code columnName})
 * pair is ignored by the builder's combined ignore-pattern list.
 *
 * <p>Two syntaxes are accepted:
 *
 * <ul>
 *   <li>{@code *.COLUMN} — matches the column in any table;</li>
 *   <li>{@code TABLE.COLUMN} — matches the specific pair only.</li>
 * </ul>
 *
 * <p>Pattern matching is case-insensitive on both segments; the
 * matcher uppercases everything internally to match DbUnit's own
 * table-name normalisation. Instances are immutable and
 * thread-safe.
 */
public class IgnorePatternMatcher {

    private final List<IgnorePattern> patterns;

    /**
     * Build a matcher from the (possibly empty) builder pattern list.
     *
     * @param rawPatterns the raw pattern strings as supplied to
     *                    {@code DbDiff.Builder.ignoring(...)}; any
     *                    pattern not matching the two accepted
     *                    syntaxes is silently dropped to match the
     *                    "tolerate noise" rule of the api
     */
    public IgnorePatternMatcher(List<String> rawPatterns) {
        List<IgnorePattern> parsed = new ArrayList<>();
        for (String raw : rawPatterns) {
            int dot = raw.indexOf('.');
            if (dot < 0) {
                continue;
            }
            String tableSegment = raw.substring(0, dot);
            String columnSegment = raw.substring(dot + 1);
            if (tableSegment.isEmpty() || columnSegment.isEmpty()) {
                continue;
            }
            parsed.add(new IgnorePattern(
                    tableSegment.toUpperCase(Locale.ROOT),
                    columnSegment.toUpperCase(Locale.ROOT)));
        }
        this.patterns = parsed;
    }

    /**
     * Whether the column should be skipped during the diff.
     *
     * @param tableName  the table the column lives in
     * @param columnName the column to test
     * @return {@code true} when at least one pattern matches
     */
    public boolean isIgnored(String tableName, String columnName) {
        String upperTable = tableName.toUpperCase(Locale.ROOT);
        String upperColumn = columnName.toUpperCase(Locale.ROOT);
        for (IgnorePattern pattern : patterns) {
            if (!pattern.column.equals(upperColumn)) {
                continue;
            }
            if (pattern.table.equals("*") || pattern.table.equals(upperTable)) {
                return true;
            }
        }
        return false;
    }

    private record IgnorePattern(String table, String column) { }
}
