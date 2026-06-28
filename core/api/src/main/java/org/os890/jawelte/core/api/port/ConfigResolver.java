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
 * configured keys the resolver can enumerate, and the caller resolves
 * each value via {@link #resolve(String)} as needed. Key enumeration
 * is best-effort for sources that do not list their keys in the
 * requested form (see {@link #resolveKeys()}).
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
 * <p><strong>Selection.</strong> The framework resolves the active
 * {@code ConfigResolver} through {@code TestContext.loadService(...)}
 * — {@code ServiceLoader}-discovered and ordered by the
 * {@code ServicePriorityResolver} (lowest {@code @Priority} wins) —
 * exactly like the other prioritized SPIs. This is mandatory rather
 * than incidental: config is read while CDI is still in
 * {@code BeforeBeanDiscovery} (so {@code @Inject} is not yet
 * available), which is why selection cannot go through CDI bean
 * resolution. To replace the resolver the framework uses, ship your
 * own implementation registered in
 * {@code META-INF/services/org.os890.jawelte.core.api.port.ConfigResolver}
 * with a lower numeric {@code @Priority} than the {@code core/impl}
 * default.
 *
 * <p>The default implementation is additionally an
 * {@code @ApplicationScoped} bean, so application-level
 * {@code @ConfigBean} code that runs after the container is up may
 * also {@code @Inject} a {@code ConfigResolver} directly. Note that a
 * CDI {@code @Alternative} only swaps such an injection point — it
 * does NOT change the resolver the framework selects via
 * {@code loadService}; use the {@code ServiceLoader}/{@code @Priority}
 * route above for that.
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
 *   <li>{@link #resolveKeys()} returns the configured keys the
 *       resolver can enumerate (best-effort for sources that do not
 *       list their keys in dot form — see the method javadoc), in an
 *       unspecified but stable order; never {@code null}.</li>
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
     * The configuration keys the resolver can enumerate. Callers that
     * need any key-iteration use case (prefix matches, regex filters,
     * hand-curated allowlists, …) walk this sequence and resolve each
     * value via {@link #resolve(String)}; the port stays
     * single-key-resolve + keys-list rather than carrying
     * domain-specific iteration helpers.
     *
     * <p><strong>Enumeration is best-effort.</strong> The default
     * MicroProfile Config-backed implementation delegates to
     * {@code Config.getPropertyNames()}, which the MP Config spec
     * defines as best-effort and source-dependent: a config source may
     * decline to list its keys, or list them in a normalized form
     * rather than the original dot-separated form. Environment-variable
     * sources in particular commonly surface keys as
     * {@code UPPER_UNDERSCORE} (or not at all). Consequently a
     * <em>prefix or pattern walk</em> over this sequence may miss keys
     * that {@link #resolve(String)} would still find for an exact key
     * — the dot-then-underscore fallback that protects single-key
     * lookups cannot be applied to enumeration, and env-var
     * normalization is lossy (separators and case are unrecoverable).
     * Callers that must iterate a key family should document it as
     * settable only via key-enumerating, dot-form sources (properties
     * files, system properties).
     *
     * <p>Order is unspecified but stable for a given resolver
     * instance. Callers must not assume a particular ordering.
     *
     * @return the enumerable configured keys; never {@code null};
     *         empty when the underlying configuration enumerates none
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
