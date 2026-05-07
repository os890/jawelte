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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.os890.jawelte.core.api.ConfigBean;
import org.os890.jawelte.core.api.port.ConfigResolver;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.impl.util.EntityScanner;

/**
 * Type-safe facade over jpa-module's MicroProfile Config keys.
 * One method per config key — every method owns its own parsing,
 * default value, and key spelling, so callers never see raw
 * {@link String} keys or split commas themselves.
 *
 * <p>The {@code @ConfigBean} stereotype meta-applies
 * {@code @ApplicationScoped}, so the CDI-managed instance lives
 * once per container. Pre-CDI callers (e.g.
 * {@code JpaCdiExtension}) construct a plain {@code new JpaConfig()};
 * the {@link #lookupResolver()} fallback bootstraps the resolver
 * lazily via {@link TestContext#loadService(Class)} on first use.
 *
 * <p>{@code @PostConstruct} sets the resolver eagerly when CDI runs
 * the bean lifecycle so the same instance is ready when the first
 * method is called from a {@code @Inject JpaConfig} consumer.
 */
@ConfigBean
public class JpaConfig {

    /** MP Config prefix whose remainder maps onto JPA bootstrap properties verbatim. */
    public static final String PERSISTENCE_PROPERTY_PREFIX = "org.os890.jawelte.module.jpa.persistence-property.";

    private static final String APP_LABEL_KEY = "org.os890.jawelte.module.jpa.app-label";

    private static final String PROTECTED_PACKAGES_KEY =
            "org.os890.jawelte.module.jpa.api.PersistenceConfig.protected-packages";

    private static final String VENDOR_VETO_ALLOWLIST_KEY =
            "org.os890.jawelte.module.jpa.vendor-veto.allowlist.packages";

    private static final String ENTITY_SCAN_WHITELIST_PACKAGES_KEY =
            "org.os890.jawelte.module.jpa.entity-scan.whitelist.packages";

    private static final String ENTITY_SCAN_WHITELIST_PATTERNS_KEY =
            "org.os890.jawelte.module.jpa.entity-scan.whitelist.patterns";

    private ConfigResolver resolver;

    /** No-arg constructor used both by CDI and by direct {@code new}. */
    public JpaConfig() {
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

    /**
     * Optional app-label override used for the file-mode H2 file
     * directory when {@code @PersistenceConfig.filePath} is empty.
     *
     * @return the configured label, or {@link Optional#empty()}
     */
    public Optional<String> appLabel() {
        return lookupResolver().resolve(APP_LABEL_KEY);
    }

    /**
     * Excluded package prefixes for {@code EntityScanner}. Comma-
     * separated value; empty entries are dropped.
     *
     * @param fallback returned when the key isn't set
     * @return the configured prefixes, or {@code fallback} when unset
     */
    public Set<String> protectedPackages(Set<String> fallback) {
        return lookupResolver().resolve(PROTECTED_PACKAGES_KEY)
                .map(JpaConfig::splitCsvToOrderedSet)
                .orElse(fallback);
    }

    /**
     * Allowlist of package prefixes the vendor-veto observer must
     * NOT veto, even when their FQCNs would otherwise match a
     * vendor-veto target prefix.
     *
     * @return the allowlist; empty when the key isn't set
     */
    public Set<String> vendorVetoAllowlist() {
        return lookupResolver().resolve(VENDOR_VETO_ALLOWLIST_KEY)
                .map(JpaConfig::splitCsvToOrderedSet)
                .orElseGet(LinkedHashSet::new);
    }

    /**
     * The optional {@code @Entity}-scan positive filter compiled
     * from the literal-prefixes key plus the regex-patterns key.
     * An {@linkplain EntityScanner.Whitelist#isEmpty() empty}
     * whitelist (both lists empty) means "no whitelist filtering".
     *
     * @return the compiled whitelist; never {@code null}
     */
    public EntityScanner.Whitelist entityScanWhitelist() {
        List<String> literals = lookupResolver().resolve(ENTITY_SCAN_WHITELIST_PACKAGES_KEY)
                .map(JpaConfig::splitCsv)
                .orElseGet(List::of);
        List<String> patternStrings = lookupResolver().resolve(ENTITY_SCAN_WHITELIST_PATTERNS_KEY)
                .map(JpaConfig::splitCsv)
                .orElseGet(List::of);
        if (literals.isEmpty() && patternStrings.isEmpty()) {
            return EntityScanner.Whitelist.empty();
        }
        List<Pattern> compiled = new ArrayList<>(patternStrings.size());
        for (String regex : patternStrings) {
            compiled.add(Pattern.compile(regex));
        }
        return new EntityScanner.Whitelist(List.copyOf(literals), List.copyOf(compiled));
    }

    /**
     * Snapshot of every MP Config entry whose key starts with
     * {@link #PERSISTENCE_PROPERTY_PREFIX}; the prefix is stripped
     * so the resulting map keys are JPA property names suitable for
     * direct merge into the bootstrap property bag.
     *
     * <p>The walk goes through {@link ConfigProvider#getConfig()}
     * directly: {@code ConfigResolver.resolve} is single-key, and
     * exposing prefix iteration on the port would force every
     * user-supplied alternative {@code ConfigResolver} to support
     * key enumeration. The semantics ("walk the active MP Config
     * sources") are uniform enough that this concession lives
     * inside the typed facade.
     *
     * @return an unmodifiable, insertion-ordered map; never {@code null}
     */
    public Map<String, String> additionalPersistenceProperties() {
        Config config = ConfigProvider.getConfig();
        Map<String, String> properties = new LinkedHashMap<>();
        for (String key : config.getPropertyNames()) {
            if (!key.startsWith(PERSISTENCE_PROPERTY_PREFIX)) {
                continue;
            }
            String propertyName = key.substring(PERSISTENCE_PROPERTY_PREFIX.length());
            config.getOptionalValue(key, String.class).ifPresent(value -> properties.put(propertyName, value));
        }
        return Map.copyOf(properties);
    }

    private ConfigResolver lookupResolver() {
        ConfigResolver local = this.resolver;
        if (local == null) {
            local = TestContext.loadService(ConfigResolver.class);
            this.resolver = local;
        }
        return local;
    }

    private static Set<String> splitCsvToOrderedSet(String csv) {
        Set<String> result = new LinkedHashSet<>();
        for (String entry : csv.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static List<String> splitCsv(String csv) {
        List<String> result = new ArrayList<>();
        for (String entry : csv.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
