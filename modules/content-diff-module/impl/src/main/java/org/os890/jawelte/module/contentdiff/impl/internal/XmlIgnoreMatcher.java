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
 * Compiles a list of XPath-style ignore patterns into regular
 * expressions and tests concrete document paths against the
 * compiled set. Patterns the matcher does not understand
 * (e.g. JSON-path syntax accidentally fed to an XML builder)
 * compile to regular expressions that match nothing, matching
 * the api contract that "mixing pattern syntaxes is silently a
 * no-op".
 *
 * <h2>Supported pattern shapes</h2>
 *
 * <ul>
 *   <li>{@code /root/field} — absolute XPath; matches whether the
 *       concrete document path carries explicit 1-based predicates
 *       or not (so {@code /orders/order/id} matches both
 *       {@code /orders/order/id} and {@code /orders/order[1]/id});</li>
 *   <li>{@code /a/b[2]/c} — explicit 1-based predicate;</li>
 *   <li>{@code //field} — recursive: element at any depth.</li>
 * </ul>
 */
public class XmlIgnoreMatcher {

    private final List<Pattern> compiledPatterns;

    private XmlIgnoreMatcher(List<Pattern> compiledPatterns) {
        this.compiledPatterns = compiledPatterns;
    }

    /**
     * Compile {@code patterns} into a matcher. Malformed individual
     * patterns are silently mapped to a regex that matches nothing —
     * the api contract states that mixing pattern syntaxes (JSON
     * patterns fed to the XML matcher and vice versa) is a no-op.
     * The XML matcher therefore never throws on shape mismatches,
     * only on patterns it recognises as malformed XPath (e.g. an
     * unclosed predicate bracket).
     *
     * @param patterns the patterns to compile
     * @return the compiled matcher
     * @throws IllegalArgumentException if an XPath-shaped pattern
     *         has an unclosed {@code [} predicate
     */
    public static XmlIgnoreMatcher of(List<String> patterns) {
        List<Pattern> compiled = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            compiled.add(toRegex(pattern));
        }
        return new XmlIgnoreMatcher(List.copyOf(compiled));
    }

    /**
     * Whether {@code path} (an XPath of the form
     * {@code /orders/order[1]/id}) is matched by any of the
     * configured ignore patterns.
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
        if (pattern.isEmpty() || pattern.charAt(0) != '/') {
            return MATCHES_NOTHING;
        }
        List<Step> steps = parseSteps(pattern);
        StringBuilder regex = new StringBuilder("^");
        for (Step step : steps) {
            if (step.descendantAxis()) {
                regex.append("(?:/[^/\\[]+(?:\\[\\d+\\])?)*");
            }
            regex.append("/").append(Pattern.quote(step.name()));
            if (step.predicate() != null) {
                regex.append("\\[").append(Pattern.quote(step.predicate())).append("\\]");
            } else {
                regex.append("(?:\\[\\d+\\])?");
            }
        }
        regex.append("$");
        return Pattern.compile(regex.toString());
    }

    private static List<Step> parseSteps(String pattern) {
        List<Step> result = new ArrayList<>();
        int index = 0;
        while (index < pattern.length()) {
            if (pattern.charAt(index) != '/') {
                throw new IllegalArgumentException(
                        "Expected '/' at position " + index + " in XML ignore pattern: " + pattern);
            }
            boolean descendantAxis = false;
            index++;
            if (index < pattern.length() && pattern.charAt(index) == '/') {
                descendantAxis = true;
                index++;
            }
            int nameStart = index;
            while (index < pattern.length()
                    && pattern.charAt(index) != '/'
                    && pattern.charAt(index) != '[') {
                index++;
            }
            String name = pattern.substring(nameStart, index);
            if (name.isEmpty()) {
                throw new IllegalArgumentException(
                        "Empty step name in XML ignore pattern: " + pattern);
            }
            String predicate = null;
            if (index < pattern.length() && pattern.charAt(index) == '[') {
                int closing = pattern.indexOf(']', index);
                if (closing == -1) {
                    throw new IllegalArgumentException(
                            "Unclosed [ in XML ignore pattern: " + pattern);
                }
                predicate = pattern.substring(index + 1, closing);
                index = closing + 1;
            }
            result.add(new Step(descendantAxis, name, predicate));
        }
        return result;
    }

    private static final Pattern MATCHES_NOTHING = Pattern.compile("(?!)");

    private record Step(boolean descendantAxis, String name, String predicate) {
    }
}
