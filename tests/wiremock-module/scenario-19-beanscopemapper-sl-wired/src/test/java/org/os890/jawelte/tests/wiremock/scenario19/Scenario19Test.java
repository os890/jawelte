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
package org.os890.jawelte.tests.wiremock.scenario19;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.port.BeanScopeMapper;
import org.os890.jawelte.module.scope.api.TestClassScoped;
import org.os890.jawelte.module.wiremock.impl.adapter.extension.remap.WireMockManagedScope;

/**
 * Scenario 19 — verifies the wiremock-module
 * {@link BeanScopeMapper} provider is wired via
 * {@code ServiceLoader}. Loads every provider on the classpath
 * with {@code ServiceLoader.load(BeanScopeMapper.class)} —
 * the same call scope-module's
 * {@code ScopeRemapCdiExtension} makes at extension load time —
 * and asserts that at least one provider has
 * {@code trigger() == WireMockManagedScope.class} and
 * {@code targetScope() == TestClassScoped.class}. This is what
 * actually drives the registry's scope upgrade in scenario 18.
 *
 * <p>No {@code @EnableWireMock} on this scenario — the SL wiring
 * is independent of any CDI bootstrap.
 */
class Scenario19Test {

    @Test
    void wireMockRegistryScopeRemapIsServiceLoaderRegistered() {
        List<BeanScopeMapper> providers = new ArrayList<>();
        for (BeanScopeMapper remap : ServiceLoader.load(BeanScopeMapper.class)) {
            providers.add(remap);
        }
        assertThat(providers)
                .as("at least the wiremock and scope-module providers are on the classpath")
                .isNotEmpty();
        assertThat(providers)
                .filteredOn(p -> p.trigger() == WireMockManagedScope.class)
                .as("exactly one provider triggers on @WireMockManagedScope")
                .hasSize(1)
                .allSatisfy(p -> assertThat(p.targetScope())
                        .as("the @WireMockManagedScope provider remaps to @TestClassScoped")
                        .isEqualTo(TestClassScoped.class));
    }
}
