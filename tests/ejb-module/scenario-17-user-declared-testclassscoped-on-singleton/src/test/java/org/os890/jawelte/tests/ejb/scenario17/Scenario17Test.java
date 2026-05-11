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
package org.os890.jawelte.tests.ejb.scenario17;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.scope.api.TestClassScoped;

/**
 * TICKET-007 scenario 17 — user-declared {@code @TestClassScoped}
 * on a {@code @Singleton} is preserved. The scope-module is on
 * the classpath, but the user's explicit annotation wins over the
 * {@code ScopeBinding.TestBeanDefaultScope} override (and over
 * ejb-module's default {@code @ApplicationScoped}).
 */
@EnableTestBeans
class Scenario17Test {

    @Inject
    BeanManager beanManager;

    @Test
    void userDeclaredTestClassScopedWins() {
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(UserScopedSingleton.class));
        assertThat(bean).isNotNull();
        assertThat(bean.getScope()).isEqualTo(TestClassScoped.class);
    }
}
