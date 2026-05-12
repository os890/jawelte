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
package org.os890.jawelte.module.contentdiff.impl.dialect;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.annotation.Priority;

import org.os890.jawelte.module.contentdiff.api.port.XmlPatternDialect;

/**
 * Default {@link XmlPatternDialect} — compiles XPath-flavoured
 * patterns into anchored regular expressions.
 *
 * <h2>Supported pattern shapes</h2>
 *
 * <ul>
 *   <li>{@code /root/field} — absolute XPath; matches whether the
 *       concrete document path carries explicit 1-based predicates
 *       or not (so {@code /orders/order/id} matches both
 *       {@code /orders/order/id} and {@code /orders/order[1]/id[1]}).</li>
 *   <li>{@code /a/b[2]/c} — explicit 1-based predicate.</li>
 *   <li>{@code //field} — descendant-or-self axis; element at any
 *       depth.</li>
 * </ul>
 *
 * <p>Patterns that don't start with {@code /} compile to a regex
 * that matches nothing — the api contract states that mixing
 * pattern syntaxes (e.g. JSON-path strings fed to an XML builder)
 * is a no-op.
 *
 * <p>Ships at {@link Priority}({@link Integer#MAX_VALUE}) and is the
 * only dialect registered in
 * {@code META-INF/services/org.os890.jawelte.module.contentdiff.api.port.XmlPatternDialect}
 * by default. Consumers swap in
 * {@link XmlGlobDialect} (or their own implementation) by
 * registering it at a lower priority value.
 *
 * <p>Stateless and thread-safe.
 */
@Priority(Integer.MAX_VALUE)
public class XPathStyleDialect implements XmlPatternDialect {

    private static final Pattern MATCHES_NOTHING = Pattern.compile("(?!)");

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public XPathStyleDialect() {
    }

    @Override
    public Pattern compile(String userPattern) {
        if (userPattern.isEmpty() || userPattern.charAt(0) != '/') {
            return MATCHES_NOTHING;
        }
        List<Step> steps = parseSteps(userPattern);
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

    private record Step(boolean descendantAxis, String name, String predicate) {
    }
}
