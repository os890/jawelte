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
}
