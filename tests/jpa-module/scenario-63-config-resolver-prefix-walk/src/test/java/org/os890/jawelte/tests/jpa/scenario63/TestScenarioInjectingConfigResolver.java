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
package org.os890.jawelte.tests.jpa.scenario63;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.Priority;

import org.os890.jawelte.core.impl.adapter.config.ConfigResolverAdapter;

/**
 * Test-only {@code ConfigResolver} at {@code @Priority(50)} — wins
 * the {@code TestContext.loadService} priority sort over the
 * default {@code ConfigResolverAdapter}. Inherits the default's
 * MP Config behaviour for every "real" key, then layers two extra
 * pieces of state on top:
 *
 * <ul>
 *   <li>{@link #INJECTED_KEY} appears in {@link #resolveKeys()} —
 *       so a caller doing the persistence-property prefix walk
 *       (jpa-module's bootstrap, post-§5.4) sees the synthetic
 *       key alongside the real MP Config ones.</li>
 *   <li>{@link #resolve(String)} returns {@link #INJECTED_VALUE}
 *       for that key — so the bootstrap fetches the override the
 *       resolver supplies, not whatever (if anything) MP Config
 *       has under it.</li>
 * </ul>
 *
 * <p>Picking <code>hibernate.format_sql</code> as the synthetic
 * property name (under the
 * <code>org.os890.jawelte.module.jpa.persistence-property.</code>
 * prefix) gives the test a value Hibernate echoes back through
 * <code>EntityManagerFactory.getProperties()</code> verbatim —
 * cleaner empirical signal than a no-op key Hibernate might filter.
 */
@Priority(50)
public class TestScenarioInjectingConfigResolver extends ConfigResolverAdapter {

    /** Full key (with the persistence-property prefix) the resolver injects on top of MP Config. */
    public static final String INJECTED_KEY =
            "org.os890.jawelte.module.jpa.persistence-property.hibernate.format_sql";

    /** Value the resolver returns for {@link #INJECTED_KEY}. */
    public static final String INJECTED_VALUE = "true";

    /** No-arg constructor required by ServiceLoader. */
    public TestScenarioInjectingConfigResolver() {
    }

    @Override
    public Optional<String> resolve(String dotKey) {
        if (INJECTED_KEY.equals(dotKey)) {
            return Optional.of(INJECTED_VALUE);
        }
        return super.resolve(dotKey);
    }

    @Override
    public Iterable<String> resolveKeys() {
        Set<String> keys = new LinkedHashSet<>();
        super.resolveKeys().forEach(keys::add);
        keys.add(INJECTED_KEY);
        return keys;
    }
}
