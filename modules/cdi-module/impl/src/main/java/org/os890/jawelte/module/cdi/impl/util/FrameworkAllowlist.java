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
package org.os890.jawelte.module.cdi.impl.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.os890.jawelte.core.api.port.ConfigResolver;
import org.os890.jawelte.core.api.port.TestContext;

/**
 * Identifies "framework-internal" types that are never vetoed and
 * never auto-mocked. Driven by a comma-separated package-prefix list
 * read from MicroProfile Config under the key
 * {@code org.os890.jawelte.module.cdi.framework-allowlist.packages}
 * via the active {@link ConfigResolver} (resolved through
 * {@link TestContext#loadService(Class)}, so the dot-then-underscore
 * fallback and any user-supplied {@code @Alternative ConfigResolver}
 * apply uniformly).
 *
 * <p>Bundled defaults ship in cdi-module/impl's
 * {@code META-INF/microprofile-config.properties} and cover
 * {@code java.}, {@code javax.}, {@code jakarta.}, OWB / Weld
 * internals, Apache DeltaSpike, and the framework root package
 * ({@code org.os890.jawelte.}). Downstream applications override or
 * extend the list by setting the same key in a higher-priority MP
 * Config source.
 */
public abstract class FrameworkAllowlist {

    /** MP Config key whose value lists the allowlist's package prefixes. */
    public static final String DOT_KEY = "org.os890.jawelte.module.cdi.framework-allowlist.packages";

    private static volatile List<String> cachedPrefixes;

    /**
     * Suppressed-instantiation constructor. The class is
     * {@code abstract} so direct {@code new} is impossible; the
     * explicit declaration silences {@code javadoc -doclint:all} on
     * the otherwise synthesized default constructor.
     */
    protected FrameworkAllowlist() {
    }

    /**
     * Whether the given type is on the framework allowlist. Walks the
     * type's class hierarchy (class + interfaces, recursively); returns
     * {@code true} as soon as any ancestor's package starts with one
     * of the configured prefixes.
     *
     * @param rawType the type to check
     * @return {@code true} if {@code rawType} (or any supertype) is
     *         in a configured framework package
     */
    public static boolean isAllowlisted(Class<?> rawType) {
        if (rawType == null) {
            return false;
        }
        List<String> prefixes = prefixes();
        return supertypeHasPrefix(rawType, prefixes);
    }

    private static List<String> prefixes() {
        List<String> local = cachedPrefixes;
        if (local != null) {
            return local;
        }
        synchronized (FrameworkAllowlist.class) {
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

    private static boolean supertypeHasPrefix(Class<?> rawType, List<String> prefixes) {
        if (matchesAnyPrefix(rawType, prefixes)) {
            return true;
        }
        Class<?> superclass = rawType.getSuperclass();
        if (superclass != null && superclass != Object.class && supertypeHasPrefix(superclass, prefixes)) {
            return true;
        }
        for (Class<?> ifc : rawType.getInterfaces()) {
            if (supertypeHasPrefix(ifc, prefixes)) {
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
