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
package org.os890.jawelte.core.api.port;

import java.util.List;

/**
 * SPI through which a non-owning module contributes its own
 * module-specific MP Config keys to a logical configuration key
 * owned by another module.
 *
 * <p>The owning module reads its own MP Config key directly via
 * {@link ConfigResolver#resolve(String)} — that key remains the
 * user-override channel for the logical concept. Contributor
 * modules ship a {@code ConfigKeyAliasProvider} via
 * {@code META-INF/services/org.os890.jawelte.core.api.port.ConfigKeyAliasProvider}
 * mapping the logical key to one or more contributor-specific keys
 * that the owner then merges into its final value.
 *
 * <p>Example: cdi-module's {@code DefaultExcludedPackageFilter}
 * owns the logical key {@code auto-mock.exclude-packages}; the
 * jpa-module and spring-data-module each ship a provider mapping
 * that logical key to their own module-specific keys so the filter
 * picks up all framework-contributed package prefixes alongside any
 * user-supplied additions in the owner's key.
 *
 * <p>Discovered via {@link java.util.ServiceLoader} by
 * {@code ConfigResolverAdapter} in {@code core/impl}. Implementations
 * must be stateless (the {@link #aliasesFor(String)} method is
 * called repeatedly across the lifetime of the JVM).
 */
public interface ConfigKeyAliasProvider {

    /**
     * Return this provider's contributor MP Config keys for the
     * given logical key.
     *
     * @param logicalKey the canonical key the caller is resolving
     * @return the contributor keys this provider declares for the
     *         logical key, possibly empty; never {@code null}
     */
    List<String> aliasesFor(String logicalKey);
}
