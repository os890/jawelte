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
import java.util.Optional;

/**
 * SPI for resolving configuration keys to their raw {@link String}
 * values. Config beans inject a {@code ConfigResolver} and call
 * {@link #resolve(String)} for every single-key lookup; for any
 * caller that needs to walk the configuration (prefix matches,
 * regex filters, exact-key sets, …) {@link #resolveKeys()} returns the
 * full universe of configured keys, and the caller resolves each
 * value via {@link #resolve(String)} as needed.
 *
 * <p>The default implementation lives in {@code core/impl}
 * ({@code ConfigResolverAdapter}) and looks up the key via
 * MicroProfile Config with a two-step strategy:
 * <ol>
 *   <li>look up the dot-separated key as given;</li>
 *   <li>if empty <em>and</em> the key contains at least one
 *       {@code .}, look up the underscore variant
 *       ({@code .} replaced with {@code _}).</li>
 * </ol>
 *
 * <p>Users provide their own {@code ConfigResolver} bean with
 * {@code @Alternative @Priority(...)} to replace the default. The
 * framework does not use {@code ServiceLoader} for this SPI; it is a
 * normal CDI bean overridable via standard CDI mechanisms.
 *
 * <p>For configuration that several modules can contribute to under
 * one umbrella concept (for example, the auto-mock exclude-packages
 * list — owned by cdi-module, extended by jpa-module and
 * spring-data-module), the caller passes a logical key to
 * {@link #resolveAliasKeysFor(String)} and gets back the union of
 * the module-specific MP Config keys that every registered
 * {@link ConfigKeyAliasProvider} contributes. The caller then
 * resolves each returned key via {@link #resolve(String)} and
 * merges the values. The owning module's own MP Config key is
 * <strong>not</strong> returned (the owner reads it directly); only
 * contributor modules' keys are aggregated here.
 *
 * <p><strong>Contract.</strong>
 * <ul>
 *   <li>{@code dotKey} on {@link #resolve(String)} must not be
 *       {@code null}; passing {@code null} throws
 *       {@link NullPointerException}.</li>
 *   <li>{@link #resolve(String)} returns {@link Optional#empty()}
 *       when no value is found for the key after all fallback
 *       attempts.</li>
 *   <li>{@link #resolveKeys()} returns every configured key the
 *       resolver knows about, in an unspecified but stable order;
 *       never {@code null}.</li>
 *   <li>{@code logicalKey} on
 *       {@link #resolveAliasKeysFor(String)} must not be
 *       {@code null}; passing {@code null} throws
 *       {@link NullPointerException}.</li>
 *   <li>{@link #resolveAliasKeysFor(String)} returns every
 *       contributor key for the given logical key in provider
 *       discovery order; never {@code null}; empty when no provider
 *       contributes for that key.</li>
 * </ul>
 */
public interface ConfigResolver {

    /**
     * Resolve the given configuration key to its raw {@link String}
     * value, applying any documented fallback logic.
     *
     * @param dotKey the dot-separated configuration key; must not be
     *               {@code null}
     * @return the resolved value, or {@link Optional#empty()} when
     *         the key has no value (after fallbacks)
     * @throws NullPointerException if {@code dotKey} is {@code null}
     */
    Optional<String> resolve(String dotKey);

    /**
     * The full universe of configuration keys the resolver knows
     * about. Callers that need any key-iteration use case (prefix
     * matches, regex filters, hand-curated allowlists, …) walk this
     * sequence and resolve each value via {@link #resolve(String)};
     * the port stays single-key-resolve + all-keys-list rather than
     * carrying domain-specific iteration helpers.
     *
     * <p>Order is unspecified but stable for a given resolver
     * instance. Callers must not assume a particular ordering.
     *
     * @return every configured key; never {@code null}; empty when
     *         the underlying configuration is empty
     */
    Iterable<String> resolveKeys();

    /**
     * Return the module-specific MP Config keys that every
     * registered {@link ConfigKeyAliasProvider} contributes for the
     * given logical key, in provider discovery order. The owning
     * module's own MP Config key is not in the result — the owner
     * is expected to resolve that one directly via
     * {@link #resolve(String)} and merge it with the aliases this
     * method returns.
     *
     * @param logicalKey the canonical key; must not be {@code null}
     * @return contributor keys, possibly empty; never {@code null}
     * @throws NullPointerException if {@code logicalKey} is
     *         {@code null}
     */
    List<String> resolveAliasKeysFor(String logicalKey);
}
