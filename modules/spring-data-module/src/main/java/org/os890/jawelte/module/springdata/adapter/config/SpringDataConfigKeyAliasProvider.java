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
package org.os890.jawelte.module.springdata.adapter.config;

import java.util.List;

import org.os890.jawelte.core.api.port.ConfigKeyAliasProvider;

/**
 * Contributes spring-data-module's framework-owned package prefix
 * ({@code org.springframework.data.}) to cdi-module's auto-mock
 * target-type exclude list. Discovered by
 * {@code ConfigResolverAdapter} via {@link java.util.ServiceLoader}.
 *
 * <p>User repositories that extend
 * {@code org.springframework.data.repository.Repository} (typically
 * indirectly through {@code JpaRepository} or
 * {@code CrudRepository}) must not be auto-mocked by cdi-module's
 * default-mock layer: {@code SpringDataRepositoryExtension} already
 * registers a real Spring Data implementation per discovered
 * interface. The supertype-walking filter in
 * {@code DefaultExcludedPackageFilter.isExcluded} matches the
 * prefix anywhere in the type hierarchy, so any user repository
 * whose hierarchy crosses {@code org.springframework.data.*} is
 * excluded from auto-mocking.
 *
 * <p>The logical key the provider switches on is the literal owner
 * key string published by cdi-module's
 * {@code DefaultExcludedPackageFilter.DOT_KEY}; the constant is not
 * imported because cdi-module/impl is not API.
 */
public class SpringDataConfigKeyAliasProvider implements ConfigKeyAliasProvider {

    private static final String AUTO_MOCK_EXCLUDE_PACKAGES_LOGICAL_KEY =
            "org.os890.jawelte.module.cdi.auto-mock.exclude-packages";

    /** Module-specific MP Config key under which spring-data-module ships its framework exclude prefix. */
    public static final String FRAMEWORK_EXCLUDE_PACKAGES_KEY =
            "org.os890.jawelte.module.springdata.auto-mock.framework-exclude-packages";

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public SpringDataConfigKeyAliasProvider() {
    }

    @Override
    public List<String> aliasesFor(String logicalKey) {
        if (AUTO_MOCK_EXCLUDE_PACKAGES_LOGICAL_KEY.equals(logicalKey)) {
            return List.of(FRAMEWORK_EXCLUDE_PACKAGES_KEY);
        }
        return List.of();
    }
}
