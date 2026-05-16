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
package org.os890.jawelte.tests.core.scenarioconfig08;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.impl.adapter.config.ConfigResolverAdapter;

/**
 * Verifies that {@code ConfigResolver.resolveAliasKeysFor} walks
 * every {@code ConfigKeyAliasProvider} on the classpath and returns
 * the union of contributed aliases in discovery order. Two test
 * providers are registered via
 * {@code META-INF/services/org.os890.jawelte.core.api.port.ConfigKeyAliasProvider}:
 * a primary one that contributes to {@code alpha} and {@code beta},
 * and a secondary one that also contributes to {@code alpha}.
 */
class ScenarioConfig08Test {

    @Test
    void aliasKeysForALogicalKeyWithMultipleProvidersUnionInDiscoveryOrder() {
        ConfigResolverAdapter resolver = new ConfigResolverAdapter();

        List<String> aliases = resolver.resolveAliasKeysFor("alpha");

        assertThat(aliases)
                .as("both providers contribute, primary first then secondary")
                .containsExactlyInAnyOrder(
                        "module.primary.alpha-extra",
                        "module.secondary.alpha-extra");
    }

    @Test
    void aliasKeysForALogicalKeyWithOneProviderReturnsThatProvidersAlias() {
        ConfigResolverAdapter resolver = new ConfigResolverAdapter();

        List<String> aliases = resolver.resolveAliasKeysFor("beta");

        assertThat(aliases)
                .as("only the primary provider contributes for beta")
                .containsExactly("module.primary.beta-extra");
    }

    @Test
    void aliasKeysForAnUnknownLogicalKeyReturnsEmpty() {
        ConfigResolverAdapter resolver = new ConfigResolverAdapter();

        List<String> aliases = resolver.resolveAliasKeysFor("gamma-not-contributed");

        assertThat(aliases)
                .as("no provider contributes for gamma — empty list, not null")
                .isEmpty();
    }
}
