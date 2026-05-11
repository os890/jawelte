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
package org.os890.jawelte.tests.ejb.scenario19;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.scope.api.TestClassScoped;

/**
 * TICKET-007 scenario 19 — scope-module on the classpath promotes
 * the {@code @Singleton} → {@code @ApplicationScoped} default to
 * {@code @Singleton} → {@code @TestClassScoped}. Verified via
 * {@code Bean.getScope()}.
 */
@EnableTestBeans
class Scenario19Test {

    @Inject
    BeanManager beanManager;

    @Test
    void singletonResolvesToTestClassScopedWhenScopeModulePresent() {
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(PlainSingleton.class));
        assertThat(bean).isNotNull();
        assertThat(bean.getScope()).isEqualTo(TestClassScoped.class);
    }
}
