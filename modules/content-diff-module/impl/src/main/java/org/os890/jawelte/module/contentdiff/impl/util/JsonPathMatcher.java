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
package org.os890.jawelte.module.contentdiff.impl.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.contentdiff.api.port.JsonPatternDialect;

/**
 * Holds a list of regular expressions compiled from user-supplied
 * JSON path patterns and tests concrete document paths against the
 * compiled set.
 *
 * <p>Pattern compilation is delegated to the active
 * {@link JsonPatternDialect}, resolved through
 * {@link TestContext#loadService(Class)} when the matcher is built.
 * Whichever dialect wins the priority sort decides the user-facing
 * grammar — the default JSONPath-flavoured dialect ships in
 * services; alternative grammars (e.g. {@code JsonGlobDialect}) are
 * activated by adding their own {@code META-INF/services} entry.
 *
 * <p>Same matcher class is used for two independent concerns inside
 * the JSON engine: matching ignore patterns and matching
 * unordered-array patterns. They share the active dialect — a
 * project that picks the glob dialect picks it for both lists.
 */
public class JsonPathMatcher {

    private final List<Pattern> compiledPatterns;

    private JsonPathMatcher(List<Pattern> compiledPatterns) {
        this.compiledPatterns = compiledPatterns;
    }

    /**
     * Compile {@code patterns} via the active
     * {@link JsonPatternDialect}.
     *
     * @param patterns the user-supplied patterns
     * @return the compiled matcher
     * @throws IllegalArgumentException if the active dialect rejects
     *         one of the patterns
     */
    public static JsonPathMatcher of(List<String> patterns) {
        JsonPatternDialect dialect = TestContext.loadService(JsonPatternDialect.class);
        List<Pattern> compiled = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            compiled.add(dialect.compile(pattern));
        }
        return new JsonPathMatcher(List.copyOf(compiled));
    }

    /**
     * Whether {@code path} (a JSON path of the form
     * {@code $.items[0].id}) is matched by any of the configured
     * patterns.
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
}
