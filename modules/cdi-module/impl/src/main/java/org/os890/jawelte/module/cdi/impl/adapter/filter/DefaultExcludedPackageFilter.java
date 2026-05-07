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
package org.os890.jawelte.module.cdi.impl.adapter.filter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import jakarta.annotation.Priority;

import org.os890.jawelte.core.api.port.ConfigResolver;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.api.port.ExcludedPackageFilter;

/**
 * Default {@link ExcludedPackageFilter}. Reads the comma-separated
 * package-prefix list from the MicroProfile Config key
 * {@code org.os890.jawelte.module.cdi.auto-mock.exclude-packages}
 * via the active {@link ConfigResolver} (resolved through
 * {@link TestContext#loadService(Class)}, so the dot-then-underscore
 * fallback and any user-supplied {@code @Alternative ConfigResolver}
 * apply uniformly). A type is excluded when any class in its
 * supertype hierarchy lives under one of the configured prefixes.
 *
 * <p>Annotated {@code @Priority(Integer.MAX_VALUE)} so any user-supplied
 * implementation with a lower priority value automatically wins via
 * the project-wide {@code ServicePriorityResolver}.
 *
 * <p>The parsed list is cached in a {@code volatile} field for the
 * filter instance's lifetime; never re-read per injection point.
 */
@Priority(Integer.MAX_VALUE)
public class DefaultExcludedPackageFilter implements ExcludedPackageFilter {

    /** MP Config key that lists the excluded package prefixes. */
    public static final String DOT_KEY = "org.os890.jawelte.module.cdi.auto-mock.exclude-packages";

    private volatile List<String> cachedPrefixes;

    /** No-arg constructor required by {@code ServiceLoader}. */
    public DefaultExcludedPackageFilter() {
    }

    @Override
    public boolean isExcluded(Class<?> rawType) {
        if (rawType == null) {
            return false;
        }
        List<String> prefixes = prefixes();
        if (prefixes.isEmpty()) {
            return false;
        }
        return supertypeMatches(rawType, prefixes);
    }

    private List<String> prefixes() {
        List<String> local = cachedPrefixes;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedPrefixes == null) {
                cachedPrefixes = readPrefixes();
            }
            return cachedPrefixes;
        }
    }

    private static List<String> readPrefixes() {
        ConfigResolver resolver = TestContext.loadService(ConfigResolver.class);
        return resolver.resolve(DOT_KEY)
                .map(value -> Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList())
                .orElseGet(Collections::emptyList);
    }

    private static boolean supertypeMatches(Class<?> rawType, List<String> prefixes) {
        if (matchesAnyPrefix(rawType, prefixes)) {
            return true;
        }
        Class<?> superclass = rawType.getSuperclass();
        if (superclass != null && superclass != Object.class && supertypeMatches(superclass, prefixes)) {
            return true;
        }
        for (Class<?> ifc : rawType.getInterfaces()) {
            if (supertypeMatches(ifc, prefixes)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAnyPrefix(Class<?> rawType, List<String> prefixes) {
        String packageName = rawType.getPackageName() + ".";
        for (String prefix : prefixes) {
            if (packageName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
