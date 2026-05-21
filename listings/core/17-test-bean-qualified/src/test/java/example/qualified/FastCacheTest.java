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
package example.qualified;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.TestBean;

/**
 * @TestBean(bean = InMemoryFastCache.class) activates a qualified
 * @Alternative. The alternative carries @FastCache, so it replaces the
 * @Inject @FastCache injection point — not any other Cache injection
 * point. The qualifier travels with the alternative class declaration;
 * @TestBean does not name qualifiers explicitly.
 */
@EnableTestBeans
@TestBean(bean = InMemoryFastCache.class)
class FastCacheTest {

    @Inject
    @FastCache
    Cache fastCache;

    @Test
    void qualifiedAlternativeReplacesQualifiedInjection() {
        assertThat(fastCache.get("k")).isEqualTo("in-memory:k");
    }
}
