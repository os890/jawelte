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
package org.os890.jawelte.tests.jaxrs.scenario22;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jaxrs.impl.TestUrlHolder;

/**
 * Scenario 22 — DEFAULT behaviour: with jaxrs-module on the
 * classpath but scope-module ABSENT, {@code TestUrlHolder} keeps its
 * declared {@code @ApplicationScoped} scope. jaxrs-module ships the
 * {@code TestUrlScopeRemap} {@code BeanScopeMapper} provider, but its
 * {@code targetScope()} resolves to {@code null} (the MP Config key
 * {@code org.os890.jawelte.module.jaxrs.test-url.default-scope} is
 * only shipped by scope-module), so core/impl's
 * {@code ScopeRemapCdiExtension} skips the remap and the bean is left
 * untouched.
 *
 * <p>This module parents to {@code tests/cdi-module/pom.xml} so
 * scope-module is not on the classpath. Confirms adding the upgrade
 * mechanism did not change the no-scope-module default.
 */
@EnableTestBeans
class Scenario22Test {

    @Inject
    private BeanManager beanManager;

    @Test
    void testUrlHolderStaysApplicationScopedWithoutScopeModule() {
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(TestUrlHolder.class));
        assertThat(bean)
                .as("exactly one TestUrlHolder bean is registered")
                .isNotNull();
        assertThat(bean.getScope())
                .as("without scope-module the @JaxRsManagedScope remap is skipped — "
                        + "TestUrlHolder keeps @ApplicationScoped")
                .isEqualTo(ApplicationScoped.class);
    }
}
