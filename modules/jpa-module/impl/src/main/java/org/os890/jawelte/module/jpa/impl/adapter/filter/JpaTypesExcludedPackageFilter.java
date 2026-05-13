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
package org.os890.jawelte.module.jpa.impl.adapter.filter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import jakarta.annotation.Priority;

import org.os890.jawelte.core.api.port.ConfigResolver;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.api.port.ExcludedPackageFilter;

/**
 * cdi-module {@link ExcludedPackageFilter} alternative that ships
 * with jpa-module/impl. Suppresses auto-mocking of types in
 * {@code jakarta.persistence.*} and {@code jakarta.transaction.*}
 * because jpa-module already provides real synthetic beans for
 * {@link jakarta.persistence.EntityManager},
 * {@link jakarta.persistence.EntityManagerFactory}, and
 * {@link jakarta.transaction.UserTransaction}; cdi-module's
 * auto-mock layer would otherwise register parallel mock beans
 * during {@code AfterBeanDiscovery} (extension ordering not being
 * guaranteed) and CDI deployment fails with
 * {@code AmbiguousResolutionException}.
 *
 * <p>This filter only applies when {@code jpa-module/impl} is on
 * the classpath — its {@code META-INF/services} registration ships
 * with that jar, so a project that brings in
 * {@code cdi-module/impl} but <em>not</em> {@code jpa-module/impl}
 * keeps the default auto-mock-everything behaviour for JPA / JTA
 * types (the mocks may indeed be wanted there).
 *
 * <p>Configuration via the same MP Config keys cdi-module's default
 * filter uses ({@code org.os890.jawelte.module.cdi.auto-mock.exclude-packages}
 * for target-type excludes and
 * {@code org.os890.jawelte.module.cdi.auto-mock.exclude-owning-bean-packages}
 * for owning-bean excludes) is honoured on top of the JPA-provided
 * defaults so user-supplied excludes still apply. The owning-bean
 * defaults shipped in cdi-module/impl's
 * {@code META-INF/microprofile-config.properties} cover the
 * CDI-runtime infrastructure (Weld / OWB / DeltaSpike / SmallRye).
 *
 * <p>{@code @Priority(Integer.MAX_VALUE - 1)} — one rank ahead of
 * cdi-module's {@code DefaultExcludedPackageFilter}, so this
 * filter wins via {@code TestContext.loadService}.
 */
@Priority(Integer.MAX_VALUE - 1)
public class JpaTypesExcludedPackageFilter implements ExcludedPackageFilter {

    /** MP Config key (same as cdi-module's default filter) for target-type excludes. */
    public static final String USER_CONFIG_KEY = "org.os890.jawelte.module.cdi.auto-mock.exclude-packages";

    /** MP Config key (same as cdi-module's default filter) for owning-bean excludes. */
    public static final String OWNING_BEAN_CONFIG_KEY =
            "org.os890.jawelte.module.cdi.auto-mock.exclude-owning-bean-packages";

    /** Package prefixes for types jpa-module owns (no auto-mocking). */
    private static final Set<String> JPA_PROVIDED_PREFIXES = Set.of(
            "jakarta.persistence.",
            "jakarta.transaction.");

    private volatile List<String> cachedUserPrefixes;
    private volatile List<String> cachedOwningBeanPrefixes;

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public JpaTypesExcludedPackageFilter() {
    }

    @Override
    public boolean isExcluded(Class<?> rawType) {
        if (rawType == null) {
            return false;
        }
        for (String prefix : JPA_PROVIDED_PREFIXES) {
            if (supertypeMatches(rawType, prefix)) {
                return true;
            }
        }
        for (String prefix : userPrefixes()) {
            if (supertypeMatches(rawType, prefix)) {
                return true;
            }
        }
        return false;
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

    private List<String> userPrefixes() {
        List<String> local = cachedUserPrefixes;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedUserPrefixes == null) {
                cachedUserPrefixes = readPrefixes(USER_CONFIG_KEY);
            }
            return cachedUserPrefixes;
        }
    }

    private List<String> owningBeanPrefixes() {
        List<String> local = cachedOwningBeanPrefixes;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedOwningBeanPrefixes == null) {
                cachedOwningBeanPrefixes = readPrefixes(OWNING_BEAN_CONFIG_KEY);
            }
            return cachedOwningBeanPrefixes;
        }
    }

    private static List<String> readPrefixes(String key) {
        ConfigResolver resolver = TestContext.loadService(ConfigResolver.class);
        if (resolver == null) {
            return Collections.emptyList();
        }
        return resolver.resolve(key)
                .map(value -> Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList())
                .orElseGet(Collections::emptyList);
    }

    private static boolean supertypeMatches(Class<?> rawType, String prefix) {
        if (matchesPrefix(rawType, prefix)) {
            return true;
        }
        Class<?> superclass = rawType.getSuperclass();
        if (superclass != null && superclass != Object.class && supertypeMatches(superclass, prefix)) {
            return true;
        }
        for (Class<?> interfaceType : rawType.getInterfaces()) {
            if (supertypeMatches(interfaceType, prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPrefix(Class<?> rawType, String prefix) {
        return (rawType.getPackageName() + ".").startsWith(prefix);
    }
}
