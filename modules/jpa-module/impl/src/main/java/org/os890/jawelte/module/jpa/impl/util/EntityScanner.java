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
package org.os890.jawelte.module.jpa.impl.util;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

import jakarta.persistence.Entity;

import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.UrlSet;
import org.apache.xbean.finder.archive.ClasspathArchive;

/**
 * Annotation scanner backed by Apache xbean-finder. Walks the
 * given {@link ClassLoader}'s classpath via xbean's
 * {@link ClasspathArchive} and {@link AnnotationFinder} (which
 * scans bytecode without invoking {@link Class#forName(String)} for
 * non-matching types) and returns the FQCNs of every type carrying
 * {@code @jakarta.persistence.Entity}.
 *
 * <p>Honours two filter knobs:
 *
 * <ul>
 *   <li><strong>Excluded package prefixes</strong> — any FQCN
 *       starting with one of these is dropped from the result.
 *       Used to keep the JDK / Jakarta APIs / vendor runtimes /
 *       jawelte's own packages out of the EMF's class list.</li>
 *   <li><strong>Optional whitelist</strong> ({@link Whitelist}) —
 *       when present, every returned FQCN must additionally match
 *       at least one literal package prefix or compiled regex
 *       pattern in the whitelist. An empty whitelist (the default)
 *       means "no whitelist filtering" and the exclude-only
 *       behaviour stands.</li>
 * </ul>
 *
 * <p>Cached per {@link ClassLoader}: the unfiltered scan result is
 * computed once and reused for any caller-supplied exclude/whitelist
 * combination.
 */
public abstract class EntityScanner {

    private static final Logger LOG = System.getLogger(EntityScanner.class.getName());

    /**
     * Default exclude list returned by
     * {@link #defaultExcludedPackagePrefixes()}. Covers the JDK,
     * Jakarta APIs, the bundled Hibernate / H2 / CDI runtimes, and
     * the common test-time libraries (Mockito, ByteBuddy, JUnit,
     * OpenTest4J) plus jawelte's own internal packages — none of
     * those carry user {@code @Entity} types and skipping them
     * shaves measurable time off the first-method scan.
     */
    private static final Set<String> DEFAULT_EXCLUDED_PACKAGE_PREFIXES = Set.of(
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

    /** Cached scan result per {@link ClassLoader}; weak keys avoid pinning. */
    private static final WeakHashMap<ClassLoader, Set<String>> SCAN_CACHE = new WeakHashMap<>();

    /**
     * Suppressed-instantiation constructor. The class is
     * {@code abstract} so direct {@code new} is impossible; the
     * explicit declaration silences {@code javadoc -doclint:all} on
     * the otherwise synthesised default constructor.
     */
    protected EntityScanner() {
    }

    /**
     * Scan the calling thread's context classpath for
     * {@code @Entity}-annotated types, with no whitelist filtering.
     *
     * @param excludedPackagePrefixes package prefixes to drop; never {@code null}
     * @return entity FQCNs matching the exclude filter, in classpath
     *         traversal order
     */
    public static Set<String> scan(Set<String> excludedPackagePrefixes) {
        return scan(excludedPackagePrefixes, Whitelist.empty());
    }

    /**
     * Scan with both an exclude filter and an optional whitelist.
     *
     * @param excludedPackagePrefixes package prefixes to drop
     * @param whitelist               optional positive filter; entities not
     *                                matching at least one literal prefix or
     *                                regex pattern are dropped. An
     *                                {@linkplain Whitelist#isEmpty() empty}
     *                                whitelist means no whitelist filtering.
     * @return entity FQCNs surviving both filters, in classpath order
     */
    public static Set<String> scan(Set<String> excludedPackagePrefixes, Whitelist whitelist) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Set<String> allFqcns = scanAllForClassLoader(classLoader);
        Set<String> filtered = new LinkedHashSet<>();
        for (String fqcn : allFqcns) {
            if (matchesExclude(fqcn, excludedPackagePrefixes)) {
                continue;
            }
            if (!whitelist.isEmpty() && !whitelist.matches(fqcn)) {
                continue;
            }
            filtered.add(fqcn);
        }
        return Collections.unmodifiableSet(filtered);
    }

    /**
     * Recommended baseline list of excluded package prefixes —
     * see {@link EntityScanner} class-level Javadoc for rationale.
     *
     * @return an unmodifiable, insertion-ordered set; never {@code null}
     */
    public static Set<String> defaultExcludedPackagePrefixes() {
        return DEFAULT_EXCLUDED_PACKAGE_PREFIXES;
    }

    private static Set<String> scanAllForClassLoader(ClassLoader classLoader) {
        synchronized (SCAN_CACHE) {
            Set<String> cached = SCAN_CACHE.get(classLoader);
            if (cached != null) {
                return cached;
            }
            Set<String> entities = new LinkedHashSet<>();
            try {
                List<URL> urls = new UrlSet(classLoader).getUrls();
                AnnotationFinder finder = new AnnotationFinder(new ClasspathArchive(classLoader, urls));
                for (Class<?> entityClass : finder.findAnnotatedClasses(Entity.class)) {
                    entities.add(entityClass.getName());
                }
            } catch (RuntimeException | java.io.IOException scanFailure) {
                // xbean wraps most archive-read errors in RuntimeException;
                // UrlSet may surface raw IOException. Either way the scan
                // is best-effort and a single bad classpath entry must
                // not break the bootstrap.
                LOG.log(Level.WARNING, "xbean-finder @Entity scan failed; returning partial result", scanFailure);
            }
            Set<String> result = Collections.unmodifiableSet(entities);
            SCAN_CACHE.put(classLoader, result);
            return result;
        }
    }

    private static boolean matchesExclude(String className, Set<String> excludes) {
        for (String prefix : excludes) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Optional positive filter applied on top of the exclude list.
     * Combines literal package-prefix matches with compiled regex
     * patterns: an FQCN passes when at least one literal or one
     * pattern matches it. An empty whitelist means "no whitelist
     * filtering" and falls through.
     *
     * @param literalPackagePrefixes literal package prefixes (e.g.
     *                               {@code "com.example.domain."}); each
     *                               compared via {@link String#startsWith(String)}
     * @param patterns               compiled regex patterns; each compared
     *                               via {@link Pattern#matcher(CharSequence)}
     *                               with {@code matches()}
     */
    public record Whitelist(List<String> literalPackagePrefixes, List<Pattern> patterns) {

        /**
         * Build the no-op whitelist (empty literal list, empty pattern
         * list) — {@link #isEmpty()} returns {@code true} on it and
         * {@link EntityScanner#scan(Set, Whitelist)} skips the
         * whitelist filter pass entirely.
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
         * Whether {@code fqcn} matches at least one literal prefix
         * or one regex pattern in this whitelist.
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
