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
 * Alternative {@link XmlPatternDialect} that accepts glob-style
 * patterns: {@code **} matches zero or more path segments,
 * {@code *} matches exactly one segment. Concrete-path predicates
 * (e.g. {@code [1]}) are tolerated on every literal step so a
 * predicate-free pattern matches paths the engine emits with
 * explicit indices.
 *
 * <h2>Supported pattern shapes</h2>
 *
 * <ul>
 *   <li>{@code /root/field} — exact path; matches whether the
 *       concrete document path carries explicit 1-based predicates
 *       or not.</li>
 *   <li>{@code /*&#47;child} — single-segment wildcard; matches
 *       {@code /anything/child} but not {@code /a/b/child}.</li>
 *   <li>{@code /**&#47;leaf} — zero-or-more wildcard; matches
 *       {@code /leaf}, {@code /a/leaf}, {@code /a/b/c/leaf}, etc.</li>
 * </ul>
 *
 * <p>Shipped in {@code content-diff-module-impl} but <em>not</em>
 * registered in the default
 * {@code META-INF/services/org.os890.jawelte.module.contentdiff.api.port.XmlPatternDialect}
 * file. Consumers who prefer the glob grammar over the XPath
 * default add their own services entry pointing at this class — the
 * project-wide priority resolver then picks it over
 * {@link XPathStyleDialect}.
 *
 * <p>Stateless and thread-safe.
 */
@Priority(Integer.MAX_VALUE - 1)
public class XmlGlobDialect implements XmlPatternDialect {

    private static final Pattern MATCHES_NOTHING = Pattern.compile("(?!)");

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public XmlGlobDialect() {
    }

    @Override
    public Pattern compile(String userPattern) {
        if (userPattern.isEmpty() || userPattern.charAt(0) != '/') {
            return MATCHES_NOTHING;
        }
        List<String> segments = splitOnSlashes(userPattern);
        StringBuilder regex = new StringBuilder("^");
        for (String segment : segments) {
            if ("**".equals(segment)) {
                regex.append("(?:/[^/\\[]+(?:\\[\\d+\\])?)*");
            } else if ("*".equals(segment)) {
                regex.append("/[^/\\[]+(?:\\[\\d+\\])?");
            } else {
                int bracketStart = segment.indexOf('[');
                if (bracketStart < 0) {
                    regex.append("/")
                            .append(Pattern.quote(segment))
                            .append("(?:\\[\\d+\\])?");
                } else {
                    String name = segment.substring(0, bracketStart);
                    String predicateAndRest = segment.substring(bracketStart);
                    regex.append("/")
                            .append(Pattern.quote(name))
                            .append(Pattern.quote(predicateAndRest));
                }
            }
        }
        regex.append("$");
        return Pattern.compile(regex.toString());
    }

    private static List<String> splitOnSlashes(String pattern) {
        // pattern starts with '/'. Tokenize between slashes; consecutive
        // slashes (e.g. `/**/`) produce an empty segment which we never
        // emit, and the `**` itself shows up as its own segment between
        // the two slashes that bracket it.
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 1; index < pattern.length(); index++) {
            char character = pattern.charAt(index);
            if (character == '/') {
                if (current.length() > 0) {
                    segments.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(character);
            }
        }
        if (current.length() > 0) {
            segments.add(current.toString());
        }
        return segments;
    }
}
