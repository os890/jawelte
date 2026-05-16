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

import java.util.List;

import org.os890.jawelte.core.api.port.ConfigKeyAliasProvider;

/**
 * First test-classpath provider. Contributes one alias to logical
 * key {@code alpha} and one alias to logical key {@code beta}.
 */
public class TestScenarioPrimaryAliasProvider implements ConfigKeyAliasProvider {

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public TestScenarioPrimaryAliasProvider() {
    }

    @Override
    public List<String> aliasesFor(String logicalKey) {
        return switch (logicalKey) {
            case "alpha" -> List.of("module.primary.alpha-extra");
            case "beta" -> List.of("module.primary.beta-extra");
            default -> List.of();
        };
    }
}
