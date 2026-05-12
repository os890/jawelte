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
package org.os890.jawelte.tests.ejb.scenario11;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * TICKET-007 scenario 11 — {@code @jakarta.inject.Singleton} (the CDI
 * pseudo-scope) is not processed by ejb-module's default mapper.
 * The bean's scope stays {@code @jakarta.inject.Singleton}; no
 * {@code @ApplicationScoped} override, no implicit
 * {@code @Transactional} added.
 */
@EnableTestBeans
class Scenario11Test {

    @Inject
    BeanManager beanManager;

    @Test
    void injectSingletonIsLeftAlone() {
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(InjectSingletonBean.class));
        assertThat(bean).isNotNull();
        assertThat(bean.getScope()).isEqualTo(Singleton.class);
    }
}
