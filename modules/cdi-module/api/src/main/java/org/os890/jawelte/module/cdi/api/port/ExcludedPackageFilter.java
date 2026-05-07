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
package org.os890.jawelte.module.cdi.api.port;

/**
 * Auto-mocking exclude policy. cdi-module's CDI Extension consults
 * the active {@code ExcludedPackageFilter} during
 * {@code AfterBeanDiscovery} for every type that is otherwise a
 * candidate for auto-mocking; a {@code true} result skips synthetic
 * mock registration for that type.
 *
 * <p>Discovered via {@code ServiceLoader} and selected by
 * {@link org.os890.jawelte.core.api.port.TestContext#loadService(Class)},
 * which routes the priority sort through the active
 * {@link org.os890.jawelte.core.api.port.ServicePriorityResolver}.
 * Lower {@code @Priority} value wins.
 *
 * <p>The default implementation lives in {@code cdi-module/impl}
 * ({@code DefaultExcludedPackageFilter}) and reads a comma-separated
 * package-prefix list from the MicroProfile Config key
 * {@code org.os890.jawelte.module.cdi.auto-mock.exclude-packages}
 * (with the standard dot-then-underscore fallback). It excludes a
 * type when any class in the type's supertype hierarchy lives under
 * one of the configured prefixes. Custom implementations replace the
 * default by providing their own {@code ServiceLoader} entry plus a
 * lower-numbered {@code @Priority}.
 *
 * <p>{@code @TestBean}-declared types bypass this filter — explicit
 * user opt-in always wins. The filter only governs <em>implicit</em>
 * auto-mocking decisions.
 *
 * <p>Implementations must work before the CDI container is up; this
 * port is consulted during {@code BeforeBeanDiscovery} /
 * {@code AfterBeanDiscovery}.
 */
public interface ExcludedPackageFilter {

    /**
     * Whether the given type should be skipped by auto-mocking.
     *
     * @param rawType the unsatisfied injection-point raw type the CDI
     *                Extension is considering for synthetic-mock
     *                registration
     * @return {@code true} to skip auto-mocking for this type;
     *         {@code false} to proceed
     */
    boolean isExcluded(Class<?> rawType);
}
