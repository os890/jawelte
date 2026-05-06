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
package org.os890.jawelte.core.impl.config;

import java.util.Objects;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.os890.jawelte.core.api.port.ConfigResolver;

/**
 * Default {@link ConfigResolver} implementation. Looks up
 * configuration values via MicroProfile Config with a two-step key
 * strategy: try the dot-separated key first, then (if the key
 * contains at least one {@code .}) retry with {@code _} substituted
 * for {@code .}.
 *
 * <p>The {@link Config} instance is fetched once via
 * {@link ConfigProvider#getConfig()} and cached in a private field
 * for the lifetime of the bean. Two entry points populate the field;
 * whichever fires first wins, and the other becomes a no-op:
 * <ol>
 *   <li>{@link #init()} is annotated with {@code @PostConstruct} and
 *       runs when the bean is instantiated by a CDI container (the
 *       standard production path).</li>
 *   <li>{@link #cachedConfig()} performs the same initialization
 *       lazily on the first {@link #resolve(String)} call when no
 *       {@code @PostConstruct} ran (covers unit tests that construct
 *       this class directly without a CDI container).</li>
 * </ol>
 *
 * <p>The MicroProfile Config specification guarantees that a
 * {@code Config} instance is valid for the lifetime of the
 * application, so caching the reference is safe and required by
 * TICKET-002's performance NFR.
 *
 * <p>Users replace this default by providing their own
 * {@code ConfigResolver} bean annotated with
 * {@code @Alternative @Priority(...)} — standard CDI mechanics, no
 * {@code ServiceLoader} involved.
 */
@ApplicationScoped
public class ConfigResolverAdapter implements ConfigResolver {

    private Config config;

    /**
     * No-arg constructor used both by the CDI runtime and by direct
     * instantiation in tests.
     */
    public ConfigResolverAdapter() {
    }

    /**
     * Populates the cached {@link Config} reference when the bean is
     * managed by a CDI container.
     */
    @PostConstruct
    void init() {
        if (this.config == null) {
            this.config = ConfigProvider.getConfig();
        }
    }

    @Override
    public Optional<String> resolve(String dotKey) {
        Objects.requireNonNull(dotKey, "dotKey");
        Config resolved = cachedConfig();
        Optional<String> direct = resolved.getOptionalValue(dotKey, String.class);
        if (direct.isPresent()) {
            return direct;
        }
        if (dotKey.indexOf('.') < 0) {
            return Optional.empty();
        }
        return resolved.getOptionalValue(dotKey.replace('.', '_'), String.class);
    }

    private Config cachedConfig() {
        Config local = this.config;
        if (local == null) {
            local = ConfigProvider.getConfig();
            this.config = local;
        }
        return local;
    }
}
