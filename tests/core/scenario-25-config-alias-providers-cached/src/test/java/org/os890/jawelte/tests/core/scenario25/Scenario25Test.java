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
package org.os890.jawelte.tests.core.scenario25;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.impl.adapter.config.ConfigResolverAdapter;

/**
 * {@code ConfigResolverAdapter.resolveAliasKeysFor} must enumerate its
 * {@link org.os890.jawelte.core.api.port.ConfigKeyAliasProvider} set once and
 * cache it, not re-run {@code ServiceLoader.load} on every call. The cached
 * results must still be correct across calls.
 *
 * <p>Driven directly (no CDI container): a counting test provider records its
 * construction count, which equals the number of {@code ServiceLoader}
 * enumerations.
 */
public class Scenario25Test {

    /** No-arg constructor. */
    public Scenario25Test() {
    }

    @Test
    public void aliasProvidersAreEnumeratedOnceAndCachedAcrossCalls() {
        TestScenarioCountingAliasProvider.CONSTRUCTION_COUNT.set(0);
        ConfigResolverAdapter resolver = new ConfigResolverAdapter();

        List<String> first = resolver.resolveAliasKeysFor("scenario25.logical");
        List<String> second = resolver.resolveAliasKeysFor("scenario25.logical");

        // Behaviour is preserved across calls.
        assertThat(first).containsExactly("scenario25.alias");
        assertThat(second).containsExactly("scenario25.alias");

        // The provider is instantiated once and cached. Without caching, each
        // resolveAliasKeysFor call runs a fresh ServiceLoader.load, which
        // constructs the provider again — so this count would be 2.
        assertThat(TestScenarioCountingAliasProvider.CONSTRUCTION_COUNT.get())
                .as("ConfigKeyAliasProvider must be enumerated once and cached, not per call")
                .isEqualTo(1);
    }
}
