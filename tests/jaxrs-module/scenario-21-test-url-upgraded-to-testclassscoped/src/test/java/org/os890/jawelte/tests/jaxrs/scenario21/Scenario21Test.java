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
package org.os890.jawelte.tests.jaxrs.scenario21;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jaxrs.impl.TestUrlHolder;
import org.os890.jawelte.module.scope.api.TestClassScoped;

/**
 * Scenario 21 — with scope-module on the classpath, the
 * {@code TestUrlHolder} bean (carrying the impl-internal
 * {@code @JaxRsManaged} stereotype, which contributes
 * {@code @ApplicationScoped} by default) has
 * its resolved CDI scope upgraded to
 * {@link TestClassScoped @TestClassScoped} by jaxrs-module's
 * {@code TestUrlScopeRemap} {@code BeanScopeMapper} provider, driven
 * by core/impl's {@code ScopeRemapCdiExtension} at
 * {@code ProcessAnnotatedType} time.
 *
 * <p>The second assertion is the load-bearing safeguard: a plain
 * {@code @ApplicationScoped} bean WITHOUT the marker
 * ({@link PlainApplicationScopedBean}) is left untouched — the remap
 * is keyed on the marker, not on {@code @ApplicationScoped}, so it
 * never affects arbitrary application-scoped beans.
 */
@EnableTestBeans
class Scenario21Test {

    @Inject
    private BeanManager beanManager;

    @Test
    void testUrlHolderIsUpgradedToTestClassScoped() {
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(TestUrlHolder.class));
        assertThat(bean)
                .as("exactly one TestUrlHolder bean is registered")
                .isNotNull();
        assertThat(bean.getScope())
                .as("@JaxRsManaged → @TestClassScoped remap applied when scope-module is present")
                .isEqualTo(TestClassScoped.class);
    }

    @Test
    void unmarkedApplicationScopedBeanIsNotRemapped() {
        Bean<?> bean = beanManager.resolve(
                beanManager.getBeans(PlainApplicationScopedBean.class));
        assertThat(bean)
                .as("the control bean is registered")
                .isNotNull();
        assertThat(bean.getScope())
                .as("a plain @ApplicationScoped bean without @JaxRsManaged is NOT remapped")
                .isEqualTo(ApplicationScoped.class);
    }
}
