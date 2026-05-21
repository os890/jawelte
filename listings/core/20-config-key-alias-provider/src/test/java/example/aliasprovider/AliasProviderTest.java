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
package example.aliasprovider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.port.ConfigResolver;

/**
 * The owner module's view: read its own key, plus the alias keys
 * contributed by ShippingFeaturesAliasProvider, then concatenate
 * values into one flat list of enabled feature flags.
 */
@EnableTestBeans
class AliasProviderTest {

    @Inject
    ConfigResolver configResolver;

    @Test
    void aliasProviderContributesItsKeyToTheLogicalLookup() {
        assertThat(configResolver.resolveAliasKeysFor("app.features.enabled-flags"))
                .containsExactly(ShippingFeaturesAliasProvider.SHIPPING_KEY);
    }

    @Test
    void ownerMergesItsOwnValueWithContributorValues() {
        List<String> merged = new ArrayList<>();
        configResolver.resolve("app.features.enabled-flags").ifPresent(value -> merged.addAll(split(value)));
        for (String aliasKey : configResolver.resolveAliasKeysFor("app.features.enabled-flags")) {
            configResolver.resolve(aliasKey).ifPresent(value -> merged.addAll(split(value)));
        }
        assertThat(merged).containsExactly(
                "beta-checkout", "new-search",
                "next-day-delivery", "tracking-v2");
    }

    private static List<String> split(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).toList();
    }
}
