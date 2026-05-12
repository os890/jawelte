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
package org.os890.jawelte.module.contentdiff.impl.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Compiles a list of JSON-path-style ignore patterns into regular
 * expressions and tests concrete document paths against the
 * compiled set. Patterns the matcher does not understand (e.g.
 * XPath syntax accidentally fed to a JSON builder) compile to
 * regular expressions that match nothing, matching the api
 * contract that "mixing pattern syntaxes is silently a no-op".
 *
 * <h2>Supported pattern shapes</h2>
 *
 * <ul>
 *   <li>{@code $.field} — top-level field;</li>
 *   <li>{@code $.parent.child} — nested field;</li>
 *   <li>{@code $.items[*].field} — field in every array element;</li>
 *   <li>{@code $..field} — recursive descent; field at any depth.</li>
 * </ul>
 */
public class JsonIgnoreMatcher {

    private final List<Pattern> compiledPatterns;

    private JsonIgnoreMatcher(List<Pattern> compiledPatterns) {
        this.compiledPatterns = compiledPatterns;
    }

    /**
     * Compile {@code patterns} into a matcher. Malformed individual
     * patterns throw {@link IllegalArgumentException} eagerly so
     * callers see the failure with the offending pattern in the
     * message.
     *
     * @param patterns the patterns to compile
     * @return the compiled matcher
     * @throws IllegalArgumentException if a pattern has an unclosed
     *         {@code [} bracket
     */
    public static JsonIgnoreMatcher of(List<String> patterns) {
        List<Pattern> compiled = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            compiled.add(toRegex(pattern));
        }
        return new JsonIgnoreMatcher(List.copyOf(compiled));
    }

    /**
     * Whether {@code path} (a JSON path of the form
     * {@code $.items[0].id}) is matched by any of the configured
     * ignore patterns.
     *
     * @param path the document path to test
     * @return {@code true} when at least one pattern matches
     */
    public boolean matches(String path) {
        for (Pattern compiledPattern : compiledPatterns) {
            if (compiledPattern.matcher(path).matches()) {
                return true;
            }
        }
        return false;
    }

    private static Pattern toRegex(String pattern) {
        StringBuilder regex = new StringBuilder("^");
        int index = 0;
        while (index < pattern.length()) {
            char current = pattern.charAt(index);
            if (current == '$') {
                regex.append("\\$");
                index++;
            } else if (current == '.'
                    && index + 1 < pattern.length()
                    && pattern.charAt(index + 1) == '.') {
                regex.append("(?:\\.[^.\\[]+|\\[\\d+\\])*\\.");
                index += 2;
            } else if (current == '.') {
                regex.append("\\.");
                index++;
            } else if (current == '[') {
                int closing = pattern.indexOf(']', index);
                if (closing == -1) {
                    throw new IllegalArgumentException(
                            "Unclosed [ in JSON ignore pattern: " + pattern);
                }
                String inside = pattern.substring(index + 1, closing);
                if ("*".equals(inside)) {
                    regex.append("\\[\\d+\\]");
                } else {
                    regex.append("\\[").append(Pattern.quote(inside)).append("\\]");
                }
                index = closing + 1;
            } else {
                regex.append(Pattern.quote(String.valueOf(current)));
                index++;
            }
        }
        regex.append("$");
        return Pattern.compile(regex.toString());
    }
}
