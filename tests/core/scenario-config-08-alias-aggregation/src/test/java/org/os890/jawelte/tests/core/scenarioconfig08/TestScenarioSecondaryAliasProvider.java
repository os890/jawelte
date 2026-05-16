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
 * Second test-classpath provider. Contributes one alias to logical
 * key {@code alpha} (overlaps with the primary provider's logical
 * key, contributes a distinct alias key) and is silent for
 * {@code beta}.
 */
public class TestScenarioSecondaryAliasProvider implements ConfigKeyAliasProvider {

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public TestScenarioSecondaryAliasProvider() {
    }

    @Override
    public List<String> aliasesFor(String logicalKey) {
        if ("alpha".equals(logicalKey)) {
            return List.of("module.secondary.alpha-extra");
        }
        return List.of();
    }
}
