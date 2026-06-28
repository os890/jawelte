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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.os890.jawelte.core.api.port.ConfigKeyAliasProvider;

/**
 * Test-only {@link ConfigKeyAliasProvider} that counts how many times it is
 * constructed. {@code ServiceLoader.load(...)} instantiates a fresh provider
 * per enumeration, so the construction count reveals whether
 * {@code ConfigResolverAdapter} re-enumerates providers per call (count grows)
 * or enumerates once and caches them (count stays at 1).
 */
public class TestScenarioCountingAliasProvider implements ConfigKeyAliasProvider {

    /** Incremented on every construction (i.e. every ServiceLoader enumeration). */
    public static final AtomicInteger CONSTRUCTION_COUNT = new AtomicInteger();

    /** Public no-arg constructor required by {@code ServiceLoader}. */
    public TestScenarioCountingAliasProvider() {
        CONSTRUCTION_COUNT.incrementAndGet();
    }

    @Override
    public List<String> aliasesFor(String logicalKey) {
        if ("scenario25.logical".equals(logicalKey)) {
            return List.of("scenario25.alias");
        }
        return List.of();
    }
}
