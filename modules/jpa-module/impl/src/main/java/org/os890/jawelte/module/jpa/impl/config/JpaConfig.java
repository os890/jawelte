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
package org.os890.jawelte.module.jpa.impl.config;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.os890.jawelte.core.api.ConfigBean;
import org.os890.jawelte.core.api.port.ConfigResolver;
import org.os890.jawelte.core.api.port.TestContext;

/**
 * Type-safe facade over jpa-module's MicroProfile Config keys. One
 * method per config key — every method owns its own parsing, default
 * value, and key spelling, so callers never see raw {@link String}
 * keys or split commas themselves.
 *
 * <p>Lives in {@code impl.config} (not under {@code impl.adapter}) —
 * a {@code @ConfigBean} facade is config <em>for</em> the adapters,
 * not an adapter / port impl in its own right.
 *
 * <p>The {@code @ConfigBean} stereotype meta-applies
 * {@code @ApplicationScoped}, so the CDI-managed instance lives once
 * per container. Pre-CDI callers (e.g. {@code JpaCdiExtension})
 * construct a plain {@code new JpaConfig()}; the
 * {@link #lookupResolver()} fallback bootstraps the resolver lazily
 * via {@link TestContext#loadService(Class)} on first use.
 *
 * <p>{@code @PostConstruct} sets the resolver eagerly when CDI runs
 * the bean lifecycle so the same instance is ready when the first
 * method is called from a {@code @Inject JpaConfig} consumer.
 */
@ConfigBean
public class JpaConfig {

    /** MP Config prefix whose remainder maps onto JPA bootstrap properties verbatim. */
    public static final String PERSISTENCE_PROPERTY_PREFIX = "org.os890.jawelte.module.jpa.persistence-property.";

    private ConfigResolver resolver;

    /** No-arg constructor used both by CDI and by direct {@code new}. */
    public JpaConfig() {
    }

    /**
     * Snapshot of every config entry whose key starts with
     * {@link #PERSISTENCE_PROPERTY_PREFIX}; the prefix is stripped
     * so the resulting map keys are JPA property names suitable for
     * direct merge into the bootstrap property bag.
     *
     * <p>Goes through the active {@link ConfigResolver} — uses
     * {@link ConfigResolver#resolveKeys()} to enumerate the
     * universe and {@link ConfigResolver#resolve(String)} to fetch
     * each value — so a consumer-supplied resolver controls the
     * full set of keys jpa-module reads, including this prefix
     * walk (closes punch-list §5.4).
     *
     * @return an unmodifiable, insertion-ordered map; never {@code null}
     */
    public Map<String, String> additionalPersistenceProperties() {
        ConfigResolver resolver = lookupResolver();
        Map<String, String> properties = new LinkedHashMap<>();
        for (String key : resolver.resolveKeys()) {
            if (!key.startsWith(PERSISTENCE_PROPERTY_PREFIX)) {
                continue;
            }
            String propertyName = key.substring(PERSISTENCE_PROPERTY_PREFIX.length());
            resolver.resolve(key).ifPresent(value -> properties.put(propertyName, value));
        }
        return Map.copyOf(properties);
    }

    /**
     * Eagerly populate the {@link ConfigResolver} reference when the
     * bean is managed by CDI. Manual {@code new JpaConfig()} callers
     * fall through to the lazy {@link #lookupResolver()} branch on
     * first access.
     */
    @PostConstruct
    void init() {
        if (this.resolver == null) {
            this.resolver = TestContext.loadService(ConfigResolver.class);
        }
    }

    private ConfigResolver lookupResolver() {
        ConfigResolver local = this.resolver;
        if (local == null) {
            local = TestContext.loadService(ConfigResolver.class);
            this.resolver = local;
        }
        return local;
    }

}
