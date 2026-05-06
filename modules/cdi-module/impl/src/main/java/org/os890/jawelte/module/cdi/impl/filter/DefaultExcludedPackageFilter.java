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
package org.os890.jawelte.module.cdi.impl.filter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.Priority;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.os890.jawelte.module.cdi.api.port.ExcludedPackageFilter;

/**
 * Default {@link ExcludedPackageFilter}. Reads the comma-separated
 * package-prefix list from the MicroProfile Config key
 * {@code org.os890.jawelte.module.cdi.auto-mock.exclude-packages}
 * (with the standard dot-then-underscore fallback). A type is
 * excluded when any class in its supertype hierarchy lives under one
 * of the configured prefixes.
 *
 * <p>Annotated {@code @Priority(Integer.MAX_VALUE)} so any user-supplied
 * implementation with a lower priority value automatically wins via
 * the project-wide {@code ServicePriorityResolver}.
 *
 * <p>Loaded via {@code ServiceLoader} during CDI bootstrap, BEFORE
 * any CDI bean is available — so this class deliberately reads MP
 * Config directly via {@code ConfigProvider.getConfig()} rather than
 * routing through the {@code @Inject ConfigResolver} from TICKET-002.
 * The parsed list is cached in a {@code volatile} field for the
 * filter instance's lifetime; never re-read per injection point.
 */
@Priority(Integer.MAX_VALUE)
public class DefaultExcludedPackageFilter implements ExcludedPackageFilter {

    /** MP Config key that lists the excluded package prefixes. */
    public static final String DOT_KEY = "org.os890.jawelte.module.cdi.auto-mock.exclude-packages";

    private static final String UNDERSCORE_KEY = DOT_KEY.replace('.', '_');

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
        Config config = ConfigProvider.getConfig();
        Optional<String> value = config.getOptionalValue(DOT_KEY, String.class)
                .or(() -> config.getOptionalValue(UNDERSCORE_KEY, String.class));
        if (value.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.get().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
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
