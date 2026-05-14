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
package org.os890.jawelte.tests.scope.scenario28;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.ConfigBean;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.scope.api.TestClassScoped;

/**
 * Scenario 28 — a {@code @ConfigBean}-annotated class is remapped
 * from the stereotype's contributed {@code @ApplicationScoped} to
 * {@code @TestClassScoped} by scope-module's CDI Extension.
 * Verified by reading the bean's effective scope off the
 * {@link BeanManager} after CDI bootstrap.
 */
@EnableTestBeans
class Scenario28Test {

    @Test
    void configBeanIsRemappedToTestClassScoped() {
        BeanManager beanManager = CDI.current().getBeanManager();
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(MyConfig.class));
        assertThat(bean)
                .as("CDI bean for @ConfigBean class must be resolvable")
                .isNotNull();
        assertThat(bean.getScope())
                .as("Scope of the @ConfigBean class after the scope-module remap")
                .isEqualTo(TestClassScoped.class);
    }

    @ConfigBean
    public static class MyConfig {

        public String value() {
            return "scenario-28";
        }
    }
}
