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

import java.util.List;

import org.os890.jawelte.core.api.port.ConfigKeyAliasProvider;

/**
 * A non-owning module contributing an extra MP Config key to the
 * logical key "app.features.enabled-flags".
 *
 * <p>The owner module reads its own MP Config key directly; the
 * owner also calls ConfigResolver.resolveAliasKeysFor(logicalKey) to
 * collect contributor keys (this provider's
 * "app.shipping.features.enabled-flags") and merges their values.
 * Net effect: the consumer of feature flags sees the union of all
 * modules' contributions without any of them knowing about each other.
 */
public class ShippingFeaturesAliasProvider implements ConfigKeyAliasProvider {

    static final String LOGICAL_KEY = "app.features.enabled-flags";

    public static final String SHIPPING_KEY = "app.shipping.features.enabled-flags";

    @Override
    public List<String> aliasesFor(String logicalKey) {
        if (LOGICAL_KEY.equals(logicalKey)) {
            return List.of(SHIPPING_KEY);
        }
        return List.of();
    }
}
