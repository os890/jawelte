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
package org.os890.jawelte.tests.scope.scenario29;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.ConfigBean;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.scope.api.TestClassScoped;

/**
 * Scenario 29 — the {@code @ConfigBean} remap is unconditional when
 * scope-module is on the classpath: it happens at
 * {@code ProcessAnnotatedType} time regardless of whether any test
 * method on the active class (or the wider module surface) carries
 * a testcontrol-module annotation. This test class deliberately has
 * NO testcontrol-module artefact in play and still expects the remap.
 */
@EnableTestBeans
class Scenario29Test {

    @Test
    void configBeanRemappedEvenWithoutTestcontrolModule() {
        BeanManager beanManager = CDI.current().getBeanManager();
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(UnrelatedConfig.class));
        assertThat(bean)
                .as("CDI bean for @ConfigBean class must be resolvable without testcontrol-module in play")
                .isNotNull();
        assertThat(bean.getScope())
                .as("@ConfigBean remap must fire at CDI bootstrap whenever scope-module is on the classpath")
                .isEqualTo(TestClassScoped.class);
    }

    @ConfigBean
    public static class UnrelatedConfig {

        public int magicNumber() {
            return 42;
        }
    }
}
