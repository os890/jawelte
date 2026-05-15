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
package org.os890.jawelte.tests.wiremock.scenario18;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.scope.api.TestClassScoped;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;
import org.os890.jawelte.module.wiremock.impl.WireMockServerRegistry;

/**
 * Scenario 18 — verifies the {@code WireMockServerRegistry} bean's
 * resolved CDI scope is {@link TestClassScoped @TestClassScoped}.
 * The class is declared {@code @ApplicationScoped} and carries the
 * impl-internal {@code @WireMockManagedScope} marker; scope-module's
 * {@code ScopeRemapCdiExtension} drives the wiremock-shipped
 * {@code WireMockRegistryScopeRemap} provider during
 * {@code ProcessAnnotatedType} and rewrites the scope.
 *
 * <p>testcontrol-module is deliberately NOT on the classpath of
 * this scenario — the remap is unconditional and is driven by
 * scope-module's SPI, not by any testcontrol-presence probe.
 */
@EnableWireMock
class Scenario18Test {

    @Inject
    private BeanManager beanManager;

    @Test
    void registryResolvedScopeIsTestClassScoped() {
        var beans = beanManager.getBeans(WireMockServerRegistry.class);
        assertThat(beans)
                .as("exactly one WireMockServerRegistry bean is registered")
                .hasSize(1);
        Bean<?> bean = beans.iterator().next();
        assertThat(bean.getScope())
                .as("@WireMockManagedScope → @TestClassScoped remap applied")
                .isEqualTo(TestClassScoped.class);
    }
}
