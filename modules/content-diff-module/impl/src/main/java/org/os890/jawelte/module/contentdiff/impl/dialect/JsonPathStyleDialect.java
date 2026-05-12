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

import java.util.regex.Pattern;

import jakarta.annotation.Priority;

import org.os890.jawelte.module.contentdiff.api.port.JsonPatternDialect;

/**
 * Default {@link JsonPatternDialect} — compiles JSONPath-flavoured
 * patterns into anchored regular expressions.
 *
 * <h2>Supported pattern shapes</h2>
 *
 * <ul>
 *   <li>{@code $} — the root.</li>
 *   <li>{@code $.field} — top-level field.</li>
 *   <li>{@code $.parent.child} — nested field.</li>
 *   <li>{@code $.items[*]} — every element of an array.</li>
 *   <li>{@code $.items[*].field} — field in every array element.</li>
 *   <li>{@code $..field} — recursive descent; field at any depth.</li>
 *   <li>{@code $.items[3]} — specific (zero-based) array index.</li>
 * </ul>
 *
 * <p>Ships at {@link Priority}({@link Integer#MAX_VALUE}) and is the
 * only dialect registered in
 * {@code META-INF/services/org.os890.jawelte.module.contentdiff.api.port.JsonPatternDialect}
 * by default. Consumers swap in
 * {@link JsonGlobDialect} (or their own implementation) by
 * registering it at a lower priority value.
 *
 * <p>Stateless and thread-safe.
 */
@Priority(Integer.MAX_VALUE)
public class JsonPathStyleDialect implements JsonPatternDialect {

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public JsonPathStyleDialect() {
    }

    @Override
    public Pattern compile(String userPattern) {
        StringBuilder regex = new StringBuilder("^");
        int index = 0;
        while (index < userPattern.length()) {
            char current = userPattern.charAt(index);
            if (current == '$') {
                regex.append("\\$");
                index++;
            } else if (current == '.'
                    && index + 1 < userPattern.length()
                    && userPattern.charAt(index + 1) == '.') {
                regex.append("(?:\\.[^.\\[]+|\\[\\d+\\])*\\.");
                index += 2;
            } else if (current == '.') {
                regex.append("\\.");
                index++;
            } else if (current == '[') {
                int closing = userPattern.indexOf(']', index);
                if (closing == -1) {
                    throw new IllegalArgumentException(
                            "Unclosed [ in JSON path pattern: " + userPattern);
                }
                String inside = userPattern.substring(index + 1, closing);
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
