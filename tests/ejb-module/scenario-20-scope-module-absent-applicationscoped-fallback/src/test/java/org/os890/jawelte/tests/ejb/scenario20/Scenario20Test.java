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
package org.os890.jawelte.tests.ejb.scenario20;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * TICKET-007 scenario 20 — scope-module is NOT on the test
 * classpath. The default mapper sees an unbound
 * {@code ScopeBinding.TestBeanDefaultScope} and falls back to the
 * baseline {@code @ApplicationScoped} for {@code @Singleton}.
 */
@EnableTestBeans
class Scenario20Test {

    @Inject
    BeanManager beanManager;

    @Test
    void singletonFallsBackToApplicationScopedWhenScopeModuleAbsent() {
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(PlainSingleton.class));
        assertThat(bean).isNotNull();
        assertThat(bean.getScope()).isEqualTo(ApplicationScoped.class);
    }
}
