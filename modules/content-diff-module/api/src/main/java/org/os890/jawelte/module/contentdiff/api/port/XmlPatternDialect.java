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
package org.os890.jawelte.module.contentdiff.api.port;

import java.util.regex.Pattern;

/**
 * Pluggable compiler for the user-facing XML path-pattern syntax.
 * Decides how strings passed to
 * {@link org.os890.jawelte.module.contentdiff.api.XmlBuilder#ignoring(String...)}
 * map onto the regular expressions the XML engine matches against
 * concrete document paths.
 *
 * <p>The built-in default ships at
 * {@code @Priority(Integer.MAX_VALUE)} and implements an
 * XPath-flavoured grammar: {@code /root/element},
 * {@code //element} (descendant-or-self axis), explicit
 * {@code /a/b[2]/c} predicates. Consumers swap in a different
 * grammar by registering an alternative implementation via
 * {@code META-INF/services/org.os890.jawelte.module.contentdiff.api.port.XmlPatternDialect}
 * with a lower {@code @Priority} value — the project-wide rule of
 * "lowest priority wins" applies.
 *
 * <p>Implementations must be thread-safe — a single instance is
 * shared across every concurrent diff call. The built-ins achieve
 * this by being stateless ({@link #compile(String)} produces a
 * fresh {@link Pattern} every time and holds no instance fields).
 */
public interface XmlPatternDialect {

    /**
     * Compile a single user-supplied XML path pattern into a
     * regular expression matched against concrete XPath-style
     * paths (the shape the engine builds while walking the tree,
     * e.g. {@code /orders/order[1]/id[1]}).
     *
     * <p>Implementations decide how to treat malformed patterns:
     * the default XPath dialect throws
     * {@link IllegalArgumentException} on an unclosed predicate;
     * patterns the dialect does not recognise at all (e.g. JSON
     * syntax accidentally passed in) should compile to a regex
     * that matches nothing, matching the api contract that
     * "mixing pattern syntaxes is silently a no-op".
     *
     * @param userPattern the pattern string as written by the caller
     * @return a compiled regex anchored at both ends
     * @throws IllegalArgumentException if {@code userPattern} is
     *         malformed in a way the dialect refuses to accept
     */
    Pattern compile(String userPattern);
}
