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
package org.os890.jawelte.module.jpa.api.port;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pluggable {@code @Entity} discovery for jpa-module's CDI extension.
 * The active impl is resolved through
 * {@code TestContext.loadService(EntityScanner.class)}; jpa-module/impl
 * ships {@code XbeanFinderEntityScanner} as the default at
 * {@code @Priority(Integer.MAX_VALUE)} (the lowest-priority fallback
 * per the project's resolution rule). Consumers that need a different
 * discovery model — Quarkus build-time Jandex, Spring's
 * {@code ClassPathScanningCandidateComponentProvider}, OSGi-style
 * bundle-aware discovery — register an alternative impl at a lower
 * {@code @Priority} via {@code META-INF/services}.
 *
 * <p>The contract returns the FQCNs of every type carrying
 * {@link jakarta.persistence.Entity @Entity} on the active classpath,
 * minus everything that matches one of the supplied exclude prefixes
 * and (when a {@link Whitelist} is provided) restricted further to
 * types that match at least one literal package prefix or regex.
 */
public interface EntityScanner {

    /**
     * Recommended baseline list of excluded package prefixes — covers
     * the JDK, Jakarta APIs, the bundled Hibernate / H2 / CDI
     * runtimes, the common test-time libraries (Mockito, ByteBuddy,
     * JUnit, OpenTest4J, xbean-finder), and jawelte's own internal
     * packages. Implementations can return their own list instead;
     * the default covers what every shipping default impl needs.
     *
     * @return an unmodifiable, insertion-ordered set; never {@code null}
     */
    default Set<String> defaultExcludedPackagePrefixes() {
        return Set.of(
                "java.",
                "javax.",
                "jakarta.",
                "org.hibernate.",
                "org.h2.",
                "org.jboss.weld.",
                "org.apache.openwebbeans.",
                "org.apache.webbeans.",
                "org.apache.xbean.",
                "org.mockito.",
                "net.bytebuddy.",
                "org.junit.",
                "org.opentest4j.",
                "org.os890.jawelte.core.",
                "org.os890.jawelte.module.");
    }

    /**
     * Convenience overload — scan with no whitelist (exclude-only).
     *
     * @param excludedPackagePrefixes package prefixes to drop; never {@code null}
     * @return entity FQCNs surviving the exclude filter
     */
    default Set<String> scan(Set<String> excludedPackagePrefixes) {
        return scan(excludedPackagePrefixes, Whitelist.empty());
    }

    /**
     * Scan with both an exclude filter and an optional whitelist.
     *
     * @param excludedPackagePrefixes package prefixes to drop
     * @param whitelist               positive filter; {@link Whitelist#isEmpty()
     *                                empty} means no whitelist filtering
     * @return entity FQCNs surviving both filters
     */
    Set<String> scan(Set<String> excludedPackagePrefixes, Whitelist whitelist);

    /**
     * Optional positive filter applied on top of the exclude list.
     * Combines literal package-prefix matches with compiled regex
     * patterns: an FQCN passes when at least one literal or one
     * pattern matches. An empty whitelist means "no whitelist
     * filtering" and falls through.
     *
     * @param literalPackagePrefixes literal package prefixes (e.g.
     *                               {@code "com.example.domain."}); each
     *                               compared via {@link String#startsWith(String)}
     * @param patterns               compiled regex patterns; each compared
     *                               via {@link Pattern#matcher(CharSequence)}
     *                               with {@code matches()}
     */
    record Whitelist(List<String> literalPackagePrefixes, List<Pattern> patterns) {

        /**
         * The no-op whitelist (empty literal list, empty pattern list).
         * {@link #isEmpty()} returns {@code true} on it and the scanner
         * skips the whitelist filter pass entirely.
         *
         * @return a whitelist that matches nothing
         */
        public static Whitelist empty() {
            return new Whitelist(List.of(), List.of());
        }

        /**
         * Whether this whitelist is "no whitelist" — i.e. both the
         * literal list AND the pattern list are empty.
         *
         * @return {@code true} if no whitelist filtering should apply
         */
        public boolean isEmpty() {
            return literalPackagePrefixes.isEmpty() && patterns.isEmpty();
        }

        /**
         * Whether {@code fqcn} matches at least one literal prefix or
         * regex pattern in this whitelist.
         *
         * @param fqcn the candidate fully qualified class name
         * @return {@code true} on a match; {@code false} if no rule fires
         */
        public boolean matches(String fqcn) {
            for (String literal : literalPackagePrefixes) {
                if (fqcn.startsWith(literal)) {
                    return true;
                }
            }
            for (Pattern pattern : patterns) {
                if (pattern.matcher(fqcn).matches()) {
                    return true;
                }
            }
            return false;
        }
    }
}
