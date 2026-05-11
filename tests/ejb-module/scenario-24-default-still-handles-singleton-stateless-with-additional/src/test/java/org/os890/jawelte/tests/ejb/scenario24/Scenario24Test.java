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
package org.os890.jawelte.tests.ejb.scenario24;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * TICKET-007 scenario 24 — when an additional
 * {@code EjbAnnotationMapper} is on the classpath, {@code @Singleton}
 * and {@code @Stateless} classes that the additional mapper does not
 * claim still resolve through the default mapper. {@code @Singleton}
 * → {@code @ApplicationScoped}; {@code @Stateless} →
 * {@code @Dependent}.
 */
@EnableTestBeans
class Scenario24Test {

    @Inject
    BeanManager beanManager;

    @Test
    void singletonGoesThroughDefault() {
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(PlainSingleton.class));
        assertThat(bean.getScope()).isEqualTo(ApplicationScoped.class);
    }

    @Test
    void statelessGoesThroughDefault() {
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(PlainStateless.class));
        assertThat(bean.getScope()).isEqualTo(Dependent.class);
    }
}
