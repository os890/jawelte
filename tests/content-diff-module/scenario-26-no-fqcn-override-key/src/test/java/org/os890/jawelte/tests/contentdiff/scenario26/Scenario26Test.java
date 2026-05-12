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
package org.os890.jawelte.tests.contentdiff.scenario26;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.contentdiff.api.ContentDiff;
import org.os890.jawelte.module.contentdiff.api.port.DiffEngine;

class Scenario26Test {

    @Test
    void noPerPortMpConfigOverrideKeyExistsForDiffEngine() {
        // The DiffEngine port FQCN must NOT be a configured key — overriding the
        // active engine is done by shipping a competing impl at a lower @Priority,
        // never by setting an MP Config key that names a class.
        String fqcnKey = DiffEngine.class.getName();
        String underscoreKey = fqcnKey.replace('.', '_');
        assertThat(ConfigProvider.getConfig().getOptionalValue(fqcnKey, String.class)).isEmpty();
        assertThat(ConfigProvider.getConfig().getOptionalValue(underscoreKey, String.class)).isEmpty();

        // And the built-in engines still resolve normally with no override key set.
        ContentDiff.forJson("{}").expectedContent("{}").assertEquals();
    }
}
