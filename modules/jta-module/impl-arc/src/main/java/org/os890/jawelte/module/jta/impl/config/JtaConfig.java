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
package org.os890.jawelte.module.jta.impl.config;

import jakarta.annotation.PostConstruct;

import org.os890.jawelte.core.api.ConfigBean;
import org.os890.jawelte.core.api.port.ConfigResolver;
import org.os890.jawelte.core.api.port.TestContext;

/**
 * Type-safe facade over jta-module's MicroProfile Config keys. One
 * method per config key — every method owns its own parsing, default
 * value, and key spelling, so callers never see raw {@link String}
 * keys.
 *
 * <p>Lives in {@code impl.config} (not under {@code impl.adapter}) —
 * a {@code @ConfigBean} facade is config <em>for</em> the adapters,
 * not an adapter / port impl in its own right.
 *
 * <p>The {@code @ConfigBean} stereotype meta-applies
 * {@code @ApplicationScoped}, so the CDI-managed instance lives once
 * per container. Pre-CDI callers (e.g. {@code TransactionManagerProvider}
 * impls loaded via {@link java.util.ServiceLoader} before CDI bootstraps)
 * construct a plain {@code new JtaConfig()}; the {@link #lookupResolver()}
 * fallback bootstraps the resolver lazily via
 * {@link TestContext#loadService(Class)} on first use.
 *
 * <p>{@code @PostConstruct} sets the resolver eagerly when CDI runs
 * the bean lifecycle so the same instance is ready when the first
 * method is called from a {@code @Inject JtaConfig} consumer.
 */
@ConfigBean
public class JtaConfig {

    /**
     * Provider-agnostic JVM-wide default transaction timeout, in
     * seconds. Each {@code TransactionManagerProvider} applies it via
     * its native API: Geronimo's
     * {@code GeronimoTransactionManager(int)} constructor, Narayana's
     * {@code CoreEnvironmentBean.setDefaultTimeout(int)}, Atomikos's
     * {@code com.atomikos.icatch.default_jta_timeout} property
     * (converted to milliseconds at the boundary). The shipped
     * default 120s matches the POC's chosen value and is short enough
     * for a test bench where stuck transactions should fail fast
     * rather than hang the suite. There is no portable
     * "set JVM-default timeout" API in the JTA spec —
     * {@code TransactionManager.setTransactionTimeout(int)} is
     * per-thread and only affects the next {@code begin()} — so the
     * vendor-specific hooks are unavoidable.
     */
    private static final String DEFAULT_TX_TIMEOUT_SECONDS_KEY =
            "org.os890.jawelte.module.jta.default-tx-timeout-seconds";

    private static final int DEFAULT_TX_TIMEOUT_SECONDS_FALLBACK = 120;

    private ConfigResolver resolver;

    /** No-arg constructor used both by CDI and by direct {@code new}. */
    public JtaConfig() {
    }

    /**
     * JVM-wide default transaction timeout in seconds. Falls back to
     * {@code 120} when unset. Applied by each
     * {@code TransactionManagerProvider} via its native mechanism.
     *
     * @return the configured timeout, or {@code 120}
     */
    public int defaultTransactionTimeoutSeconds() {
        return lookupResolver().resolve(DEFAULT_TX_TIMEOUT_SECONDS_KEY)
                .map(String::trim)
                .map(Integer::parseInt)
                .orElse(DEFAULT_TX_TIMEOUT_SECONDS_FALLBACK);
    }

    /**
     * Eagerly populate the {@link ConfigResolver} reference when the
     * bean is managed by CDI. Manual {@code new JtaConfig()} callers
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
