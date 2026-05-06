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
 * Project-wide SPI for ordering {@code ServiceLoader}-discovered
 * provider instances by priority. Single source of truth for the
 * "given a list of candidates, which one is active?" question; every
 * other SPI selection in the codebase routes through
 * {@link TestContext#loadService(Class)}, which delegates to the
 * active {@code ServicePriorityResolver}.
 *
 * <p>The default implementation lives in {@code core/impl} as an
 * {@code @ApplicationScoped} CDI bean and orders by
 * {@code jakarta.annotation.Priority} value ascending; providers
 * without {@code @Priority} are treated as the lowest priority; ties
 * are broken by full class name ascending.
 *
 * <p>Test infrastructures that need a different ordering (for example
 * an integration-tooling project that orders providers by external
 * metadata rather than {@code @Priority}) ship their own
 * {@code ServicePriorityResolver} implementation and select it via
 * the bootstrap MP Config key whose name is this interface's own FQCN
 * (see {@link TestContext#loadService(Class)} for the bootstrap
 * mechanics). Every other SPI selection then automatically follows
 * the new rule with no module-by-module changes.
 */
public interface ServicePriorityResolver {

    /**
     * Return the given providers in active-first order.
     *
     * <p>The element at index {@code 0} of the returned list is the
     * active provider for ports that require a single implementation;
     * registry- or chain-style ports that genuinely need the full
     * ordered list iterate over the result.
     *
     * @param providers the unordered candidates
     * @param <T>       the SPI element type
     * @return a new list ordered active-first; empty when
     *         {@code providers} is empty
     */
    <T> List<T> sort(List<T> providers);

    /**
     * Convenience method returning the head of {@link #sort(List)}, or
     * {@code null} when {@code providers} is empty. The default
     * implementation delegates to {@code sort(...)}; resolvers may
     * override for efficiency, but the contract must remain
     * "head of sort(providers)".
     *
     * @param providers the unordered candidates
     * @param <T>       the SPI element type
     * @return the active provider, or {@code null} if none
     */
    default <T> T resolve(List<T> providers) {
        List<T> sorted = sort(providers);
        return sorted.isEmpty() ? null : sorted.get(0);
    }
}
