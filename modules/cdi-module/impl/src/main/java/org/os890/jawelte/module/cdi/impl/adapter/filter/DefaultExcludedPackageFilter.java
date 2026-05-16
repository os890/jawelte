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

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Priority;

import org.os890.jawelte.core.api.port.ConfigKeyAliasProvider;
import org.os890.jawelte.core.api.port.ConfigResolver;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.api.port.ExcludedPackageFilter;

/**
 * Default {@link ExcludedPackageFilter}. Reads two package-prefix
 * lists from MicroProfile Config via the active
 * {@link ConfigResolver} (resolved through
 * {@link TestContext#loadService(Class)}, so the dot-then-underscore
 * fallback and any user-supplied {@code @Alternative ConfigResolver}
 * apply uniformly):
 *
 * <ul>
 *   <li>{@link #DOT_KEY} feeds {@link #isExcluded(Class)} — a target
 *       type is excluded when any class in its supertype hierarchy
 *       lives under one of the configured prefixes. The owner key
 *       ({@code DOT_KEY}) is the consumer's user-override channel;
 *       contributing modules (jpa-module, spring-data-module, …)
 *       extend the list via the
 *       {@link ConfigKeyAliasProvider} SPI, mapping
 *       {@code DOT_KEY} to their own module-specific MP Config keys
 *       that ship their framework-owned package prefixes as
 *       defaults.</li>
 *   <li>{@link #OWNING_BEAN_DOT_KEY} feeds
 *       {@link #isOwningBeanExcluded(Class)} — an IP is dropped
 *       when its owning bean's package starts with one of the
 *       configured prefixes. Defaults shipped in cdi-module/impl's
 *       {@code META-INF/microprofile-config.properties} cover the
 *       CDI-runtime infrastructure ({@code org.jboss.weld.},
 *       {@code org.apache.webbeans.},
 *       {@code org.apache.deltaspike.}, {@code io.smallrye.}) whose
 *       IPs the runtime satisfies internally. Same alias-extension
 *       channel applies.</li>
 * </ul>
 *
 * <p>The owner-key string itself is also the logical key the
 * {@code ConfigKeyAliasProvider} SPI dispatches on, so contributor
 * modules switch on {@code DOT_KEY} / {@code OWNING_BEAN_DOT_KEY}
 * without inventing separate logical-key identifiers.
 *
 * <p>Annotated {@code @Priority(Integer.MAX_VALUE)} so any user-supplied
 * implementation with a lower priority value automatically wins via
 * the project-wide {@code ServicePriorityResolver}.
 *
 * <p>The parsed lists are cached in {@code volatile} fields for the
 * filter instance's lifetime; never re-read per injection point.
 */
@Priority(Integer.MAX_VALUE)
public class DefaultExcludedPackageFilter implements ExcludedPackageFilter {

    /**
     * MP Config key that lists target-type package prefixes excluded from auto-mocking.
     * Also the logical key contributors switch on via {@link ConfigKeyAliasProvider}.
     */
    public static final String DOT_KEY = "org.os890.jawelte.module.cdi.auto-mock.exclude-packages";

    /**
     * MP Config key that lists owning-bean package prefixes whose IPs are dropped before auto-mocking.
     * Also the logical key contributors switch on via {@link ConfigKeyAliasProvider}.
     */
    public static final String OWNING_BEAN_DOT_KEY =
            "org.os890.jawelte.module.cdi.auto-mock.exclude-owning-bean-packages";

    private volatile List<String> cachedPrefixes;
    private volatile List<String> cachedOwningBeanPrefixes;

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

    @Override
    public boolean isOwningBeanExcluded(Class<?> owningBeanClass) {
        if (owningBeanClass == null) {
            return false;
        }
        List<String> prefixes = owningBeanPrefixes();
        if (prefixes.isEmpty()) {
            return false;
        }
        String packageName = owningBeanClass.getPackageName() + ".";
        for (String prefix : prefixes) {
            if (packageName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private List<String> prefixes() {
        List<String> local = cachedPrefixes;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedPrefixes == null) {
                cachedPrefixes = readPrefixes(DOT_KEY);
            }
            return cachedPrefixes;
        }
    }

    private List<String> owningBeanPrefixes() {
        List<String> local = cachedOwningBeanPrefixes;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedOwningBeanPrefixes == null) {
                cachedOwningBeanPrefixes = readPrefixes(OWNING_BEAN_DOT_KEY);
            }
            return cachedOwningBeanPrefixes;
        }
    }

    private static List<String> readPrefixes(String ownerAndLogicalKey) {
        ConfigResolver resolver = TestContext.loadService(ConfigResolver.class);
        List<String> result = new ArrayList<>();
        for (String aliasKey : resolver.resolveAliasKeysFor(ownerAndLogicalKey)) {
            appendValues(resolver, aliasKey, result);
        }
        appendValues(resolver, ownerAndLogicalKey, result);
        return List.copyOf(result);
    }

    private static void appendValues(ConfigResolver resolver, String key, List<String> sink) {
        resolver.resolve(key).ifPresent(value -> {
            for (String entry : value.split(",")) {
                String trimmed = entry.trim();
                if (!trimmed.isEmpty()) {
                    sink.add(trimmed);
                }
            }
        });
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
