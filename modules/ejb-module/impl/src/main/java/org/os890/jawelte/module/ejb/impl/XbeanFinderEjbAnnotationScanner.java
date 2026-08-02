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
package org.os890.jawelte.module.ejb.impl;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.NormalScope;

import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.UrlSet;
import org.apache.xbean.finder.archive.ClasspathArchive;
import org.os890.jawelte.module.ejb.api.port.EjbAnnotationScanner;

/**
 * Default {@link EjbAnnotationScanner}: walks the context
 * classloader's classpath with xbean-finder and returns the types
 * carrying one of the configured bean-defining annotations.
 *
 * <p><strong>Cached per {@link ClassLoader} and scan configuration.
 * </strong> The walk itself is the expensive part of
 * {@code BeforeBeanDiscovery} and its outcome cannot change while a
 * classloader's classpath stays fixed, yet the scan would otherwise
 * run once per test-class container boot. The cache turns that into
 * once per suite. jpa-module's {@code XbeanFinderEntityScanner} caches
 * the same way for the same reason.
 *
 * <p>The cached value is the finished, filtered set, so the
 * configuration is part of the key: a boot that configures different
 * annotations or different exclude prefixes computes and stores its
 * own entry rather than reusing a result filtered for someone else.
 *
 * <p>Cache entries are never evicted explicitly — there is no
 * invalidation hook, because nothing inside ejb-module could drive
 * one, and cleanup code that nothing calls is worse than no cleanup
 * code. A classloader that goes away simply stops being looked up,
 * and a new one computes a fresh entry. Note that the weak key is
 * only a partial protection here: the cached {@link Class} objects
 * strongly reference the very classloader used as the key, so a live
 * entry keeps that classloader reachable. In this test framework the
 * key is the JVM's context classloader, which lives as long as the
 * process, so the distinction is academic — but it does mean the map
 * must not be treated as self-trimming.
 *
 * <p>{@code @Priority(Integer.MAX_VALUE)} — absolute fallback, so any
 * consumer-supplied scanner wins.
 */
@Priority(Integer.MAX_VALUE)
public class XbeanFinderEjbAnnotationScanner implements EjbAnnotationScanner {

    private static final Logger LOG =
            System.getLogger(XbeanFinderEjbAnnotationScanner.class.getName());

    /** Cached scan results per {@link ClassLoader}, keyed by scan configuration. */
    private static final WeakHashMap<ClassLoader, Map<ScanKey, Set<Class<?>>>> SCAN_CACHE =
            new WeakHashMap<>();

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public XbeanFinderEjbAnnotationScanner() {
    }

    @Override
    public Set<Class<?>> scan(Set<Class<? extends Annotation>> beanDefiningAnnotations,
                              Set<String> excludedPackagePrefixes) {
        if (beanDefiningAnnotations.isEmpty()) {
            return Set.of();
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        ScanKey key = new ScanKey(Set.copyOf(beanDefiningAnnotations),
                Set.copyOf(excludedPackagePrefixes));
        synchronized (SCAN_CACHE) {
            Map<ScanKey, Set<Class<?>>> perConfiguration =
                    SCAN_CACHE.computeIfAbsent(classLoader, loader -> new HashMap<>());
            Set<Class<?>> cached = perConfiguration.get(key);
            if (cached != null) {
                return cached;
            }
            Set<Class<?>> scanned = Collections.unmodifiableSet(
                    performScan(key.beanDefiningAnnotations(), key.excludedPackagePrefixes(),
                            classLoader));
            perConfiguration.put(key, scanned);
            return scanned;
        }
    }

    /**
     * Perform the actual classpath walk. Separated from
     * {@link #scan(Set, Set)} so the caching decision and the work it
     * guards stay independently readable — and so a subclass can
     * substitute or observe the walk without reimplementing the cache.
     *
     * @param beanDefiningAnnotations the annotations to look for; never empty
     * @param excludedPackagePrefixes package-name prefixes to drop
     * @param classLoader             the classloader to walk
     * @return the matching types, in classpath iteration order
     */
    protected Set<Class<?>> performScan(Set<Class<? extends Annotation>> beanDefiningAnnotations,
                                        Set<String> excludedPackagePrefixes,
                                        ClassLoader classLoader) {
        long startNanos = System.nanoTime();
        LOG.log(Level.INFO, "Scanning classpath for ejb-module bean-defining annotations");
        Set<Class<?>> matches = new LinkedHashSet<>();
        try {
            List<URL> urls = new UrlSet(classLoader).getUrls();
            AnnotationFinder finder = new AnnotationFinder(new ClasspathArchive(classLoader, urls));
            for (Class<? extends Annotation> annotationType : beanDefiningAnnotations) {
                for (Class<?> ejbClass : finder.findAnnotatedClasses(annotationType)) {
                    if (!isExcluded(ejbClass.getName(), excludedPackagePrefixes)
                            && !hasNormalScopeOrDependent(ejbClass)) {
                        matches.add(ejbClass);
                    }
                }
            }
        } catch (IOException | RuntimeException scanFailure) {
            throw new IllegalStateException(
                    "ejb-module classpath scan for configured bean-defining annotations failed; "
                            + "bootstrap aborted to surface the underlying classpath problem.",
                    scanFailure);
        }
        long durationMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        LOG.log(Level.INFO,
                "Scanned classpath for ejb-module bean-defining annotations: found "
                        + matches.size() + " in " + durationMillis + "ms");
        return matches;
    }

    private static boolean isExcluded(String className, Set<String> excludedPackagePrefixes) {
        for (String prefix : excludedPackagePrefixes) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code beanClass} already carries a bean-defining
     * normal scope or {@code @Dependent}. Such classes are already
     * discoverable under {@code bean-discovery-mode="annotated"} via
     * the standard CDI rules; the extension must skip them when
     * adding annotated types to avoid producing duplicate beans
     * (OpenWebBeans rejects this with
     * {@code DuplicateDefinitionException}).
     *
     * <p>Single pass over the class's annotations; no annotation
     * hierarchy walk. Pseudo-scopes (annotations meta-annotated with
     * {@code @jakarta.inject.Scope} only — for example
     * {@code @jakarta.inject.Singleton}) are intentionally NOT
     * treated as bean-defining here because they aren't bean-defining
     * per the CDI 4.0 spec either.
     */
    private static boolean hasNormalScopeOrDependent(Class<?> beanClass) {
        for (Annotation annotation : beanClass.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (annotationType == Dependent.class
                    || annotationType.isAnnotationPresent(NormalScope.class)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cache key: the scan configuration a cached result was produced
     * for. Two boots that configure the same annotations and the same
     * exclude prefixes may share a result; anything else must not.
     *
     * @param beanDefiningAnnotations the annotations scanned for
     * @param excludedPackagePrefixes the exclude prefixes applied
     */
    private record ScanKey(Set<Class<? extends Annotation>> beanDefiningAnnotations,
                           Set<String> excludedPackagePrefixes) {
    }
}
