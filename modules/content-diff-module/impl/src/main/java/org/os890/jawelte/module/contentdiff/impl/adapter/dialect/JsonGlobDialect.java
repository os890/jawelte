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
package org.os890.jawelte.module.contentdiff.impl.adapter.dialect;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.annotation.Priority;

import org.os890.jawelte.module.contentdiff.api.port.JsonPatternDialect;

/**
 * Alternative {@link JsonPatternDialect} that accepts segment-glob
 * patterns: a literal {@code *} segment matches zero or more
 * path segments. Array indices on the concrete path are stripped
 * before the segment-by-segment comparison, so a glob segment
 * {@code customers} matches concrete segments {@code customers}
 * and {@code customers[0]} alike.
 *
 * <h2>Supported pattern shapes</h2>
 *
 * <ul>
 *   <li>{@code $.id} — exact top-level field.</li>
 *   <li>{@code $.user.createdAt} — exact nested field.</li>
 *   <li>{@code $.*.createdAt} — {@code createdAt} at any depth under
 *       a single intermediate segment (which {@code *} can repeat
 *       zero or more times via the glob's segment semantics, so it
 *       also matches {@code $.user.profile.createdAt} etc.).</li>
 *   <li>{@code $.orders.*.id} — {@code id} at any depth under
 *       {@code orders}.</li>
 * </ul>
 *
 * <p>Shipped in {@code content-diff-module-impl} but <em>not</em>
 * registered in the default
 * {@code META-INF/services/org.os890.jawelte.module.contentdiff.api.port.JsonPatternDialect}
 * file. Consumers who prefer the glob grammar over the JSONPath
 * default add their own services entry pointing at this class — the
 * project-wide priority resolver then picks it over
 * {@link JsonPathStyleDialect} (lowest priority wins;
 * {@code @Priority(Integer.MAX_VALUE - 1)} on this class is one
 * step lower than the default).
 *
 * <p>Stateless and thread-safe.
 */
@Priority(Integer.MAX_VALUE - 1)
public class JsonGlobDialect implements JsonPatternDialect {

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public JsonGlobDialect() {
    }

    @Override
    public Pattern compile(String userPattern) {
        List<String> patternSegments = splitOnDots(userPattern);
        StringBuilder regex = new StringBuilder("^");
        boolean firstSegment = true;
        for (String segment : patternSegments) {
            if ("*".equals(segment)) {
                // Glob wildcard: zero or more path segments. A segment is
                // either ".name", ".name[N]", or "[N]" (where N is a
                // numeric array index). We expect the wildcard to be
                // preceded by the root anchor or another segment, so
                // emit the leading dot inside the repetition.
                regex.append("(?:\\.[^.\\[]+(?:\\[\\d+\\])?|\\[\\d+\\])*");
                firstSegment = false;
                continue;
            }
            if (!firstSegment) {
                regex.append("\\.");
            }
            regex.append(Pattern.quote(segment));
            // Allow an optional array-index predicate on every literal
            // segment so the same glob matches `customers` and
            // `customers[0]` alike.
            regex.append("(?:\\[\\d+\\])?");
            firstSegment = false;
        }
        regex.append("$");
        return Pattern.compile(regex.toString());
    }

    private static List<String> splitOnDots(String pattern) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < pattern.length(); index++) {
            char character = pattern.charAt(index);
            if (character == '.') {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(character);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }
}
