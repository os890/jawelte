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
package org.os890.jawelte.module.jpa.impl.adapter.scan;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import jakarta.annotation.Priority;
import jakarta.persistence.Entity;

import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.UrlSet;
import org.apache.xbean.finder.archive.ClasspathArchive;
import org.os890.jawelte.module.jpa.api.port.EntityScanner;

/**
 * Default {@link EntityScanner} shipped by jpa-module — backed by Apache
 * xbean-finder. Walks the calling thread's context classloader via
 * {@link ClasspathArchive} and {@link AnnotationFinder} (which scans
 * bytecode without invoking {@link Class#forName(String)} for non-matching
 * types) and returns the FQCNs of every type carrying
 * {@code @jakarta.persistence.Entity}.
 *
 * <p>Cached per {@link ClassLoader}: the unfiltered scan result is
 * computed once and reused for any caller-supplied exclude/whitelist
 * combination.
 *
 * <p>{@code @Priority(Integer.MAX_VALUE)} — absolute fallback. Consumers
 * who need a different discovery model (build-time Jandex, classpath
 * filtering, …) ship an alternative impl at a lower priority.
 */
@Priority(Integer.MAX_VALUE)
public class XbeanFinderEntityScanner implements EntityScanner {

    private static final Logger LOG = System.getLogger(XbeanFinderEntityScanner.class.getName());

    /** Cached scan result per {@link ClassLoader}; weak keys avoid pinning. */
    private static final WeakHashMap<ClassLoader, Set<String>> SCAN_CACHE = new WeakHashMap<>();

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public XbeanFinderEntityScanner() {
    }

    @Override
    public Set<String> scan(Set<String> excludedPackagePrefixes, Whitelist whitelist) {
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
     * Pre-populate the JVM-wide scan cache for the calling thread's
     * context classloader. Called by
     * {@code JpaLauncherSessionListener.launcherSessionOpened} when
     * the consumer opts the listener in, so the first test class
     * doesn't pay the classpath-walk latency. Idempotent — a second
     * call hits the existing cache entry. Scan failures here are
     * silenced (logged but not rethrown) so an opt-in pre-warm can
     * never block the launcher session from opening; the lazy path
     * in {@link #scan(Set, Whitelist)} will surface the failure with
     * the same diagnostic when a test actually needs an entity scan.
     */
    public static void prewarmForCurrentThread() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            scanAllForClassLoader(classLoader);
        } catch (RuntimeException prewarmFailure) {
            LOG.log(Level.WARNING,
                    "Pre-warm of @Entity scan cache failed; lazy scan will retry on first use",
                    prewarmFailure);
        }
    }

    /**
     * Drop every cached scan result. Called by
     * {@code JpaLauncherSessionListener.launcherSessionClosed} so a
     * JVM that hosts multiple Surefire suites (or a Gradle test
     * worker that gets reused) re-scans on the next session instead
     * of returning a stale result for a classloader that was
     * decommissioned and re-created with the same identity.
     */
    public static void clearScanCache() {
        synchronized (SCAN_CACHE) {
            SCAN_CACHE.clear();
        }
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
                // UrlSet may surface raw IOException. Fail-fast at scan time
                // rather than returning a partial entity set: a missing
                // @Entity surfaces later as an opaque "not a managed type"
                // when the test calls em.persist, which is much harder to
                // diagnose than the underlying classpath problem here.
                LOG.log(Level.ERROR,
                        "xbean-finder @Entity scan failed; jpa-module bootstrap aborts because "
                                + "a partial entity set would silently mask the problem",
                        scanFailure);
                throw new RuntimeException(
                        "@Entity classpath scan failed; bootstrap aborted to surface the underlying "
                                + "classpath problem (a partial entity set would otherwise lead to "
                                + "opaque \"not a managed type\" errors at em.persist time)",
                        scanFailure);
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
}
