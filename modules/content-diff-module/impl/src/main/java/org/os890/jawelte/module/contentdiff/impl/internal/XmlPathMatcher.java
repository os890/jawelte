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

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.contentdiff.api.port.XmlPatternDialect;

/**
 * Holds a list of regular expressions compiled from user-supplied
 * XML path patterns and tests concrete document paths against the
 * compiled set.
 *
 * <p>Pattern compilation is delegated to the active
 * {@link XmlPatternDialect}, resolved through
 * {@link TestContext#loadService(Class)} when the matcher is built.
 * The default {@code XPathStyleDialect} handles XPath-flavoured
 * patterns; consumers swap in alternative dialects (e.g.
 * {@code XmlGlobDialect}) via their own
 * {@code META-INF/services} entry at a lower priority value.
 */
public class XmlPathMatcher {

    private final List<Pattern> compiledPatterns;

    private XmlPathMatcher(List<Pattern> compiledPatterns) {
        this.compiledPatterns = compiledPatterns;
    }

    /**
     * Compile {@code patterns} via the active
     * {@link XmlPatternDialect}.
     *
     * @param patterns the user-supplied patterns
     * @return the compiled matcher
     * @throws IllegalArgumentException if the active dialect rejects
     *         one of the patterns
     */
    public static XmlPathMatcher of(List<String> patterns) {
        XmlPatternDialect dialect = TestContext.loadService(XmlPatternDialect.class);
        List<Pattern> compiled = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            compiled.add(dialect.compile(pattern));
        }
        return new XmlPathMatcher(List.copyOf(compiled));
    }

    /**
     * Whether {@code path} (an XPath of the form
     * {@code /orders/order[1]/id}) is matched by any of the
     * configured patterns.
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
