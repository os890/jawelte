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
package org.os890.jawelte.module.jpa.impl.adapter.config;

import java.util.List;

import org.os890.jawelte.core.api.port.ConfigKeyAliasProvider;

/**
 * Contributes jpa-module's framework-owned package prefixes to
 * cdi-module's auto-mock target-type exclude list. Discovered by
 * {@code ConfigResolverAdapter} via
 * {@link java.util.ServiceLoader}.
 *
 * <p>jpa-module already provides real synthetic beans for
 * {@code jakarta.persistence.*} and {@code jakarta.transaction.*}
 * types ({@code EntityManager}, {@code EntityManagerFactory},
 * {@code UserTransaction}); cdi-module's auto-mock layer would
 * otherwise register parallel mock beans and deployment would fail
 * with {@code AmbiguousResolutionException}. The defaults are
 * shipped in jpa-module/impl's
 * {@code META-INF/microprofile-config.properties} under
 * {@link #FRAMEWORK_EXCLUDE_PACKAGES_KEY}.
 *
 * <p>The logical key the provider switches on is the literal owner
 * key string published by cdi-module's
 * {@code DefaultExcludedPackageFilter.DOT_KEY}; the constant is not
 * imported here because cdi-module/impl is not API.
 */
public class JpaConfigKeyAliasProvider implements ConfigKeyAliasProvider {

    private static final String AUTO_MOCK_EXCLUDE_PACKAGES_LOGICAL_KEY =
            "org.os890.jawelte.module.cdi.auto-mock.exclude-packages";

    /** Module-specific MP Config key under which jpa-module ships its framework exclude prefixes. */
    public static final String FRAMEWORK_EXCLUDE_PACKAGES_KEY =
            "org.os890.jawelte.module.jpa.auto-mock.framework-exclude-packages";

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public JpaConfigKeyAliasProvider() {
    }

    @Override
    public List<String> aliasesFor(String logicalKey) {
        if (AUTO_MOCK_EXCLUDE_PACKAGES_LOGICAL_KEY.equals(logicalKey)) {
            return List.of(FRAMEWORK_EXCLUDE_PACKAGES_KEY);
        }
        return List.of();
    }
}
