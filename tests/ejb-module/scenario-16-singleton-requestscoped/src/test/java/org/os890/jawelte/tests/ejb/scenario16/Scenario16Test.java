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
package org.os890.jawelte.tests.ejb.scenario16;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * TICKET-007 scenario 16 — a {@code @Singleton} class that already
 * carries a CDI scope (here {@code @RequestScoped}) keeps that
 * scope: ejb-module skips the EJB-mapped
 * {@code @ApplicationScoped} because the class is bean-defining
 * through its own scope. The implicit {@code @Transactional} is
 * still added by the mapper — scope and transactional decisions
 * are independent — and the end-to-end transactional behaviour is
 * verified in scenarios 05, 06.
 */
@EnableTestBeans
class Scenario16Test {

    @Inject
    BeanManager beanManager;

    @Test
    void singletonWithUserDeclaredScopeKeepsThatScope() {
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(RequestScopedSingleton.class));
        assertThat(bean).isNotNull();
        assertThat(bean.getScope()).isEqualTo(RequestScoped.class);
    }
}
