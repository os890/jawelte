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
package org.os890.jawelte.tests.scope.scenario32;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.ConfigBean;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.scope.api.TestClassScoped;

/**
 * Scenario 32 — a consumer-supplied {@code BeanScopeMapper} that
 * shares the {@code @ConfigBean} trigger with scope-module's built-in
 * {@code ConfigBeanToTestClassScoped} but carries a higher precedence
 * ({@code @Priority(1)} vs the built-in's absent {@code @Priority})
 * wins the trigger. The {@code @ConfigBean} class is therefore
 * remapped to the override's {@link RequestScoped}, not the built-in's
 * {@link TestClassScoped}.
 *
 * <p>This exercises the documented override contract ("ship your own
 * higher-priority BeanScopeMapper") and guards against a regression to
 * raw {@code ServiceLoader}/classpath ordering, under which the winner
 * would be nondeterministic and a lower-numeric {@code @Priority} could
 * not reliably override a built-in remap.
 *
 * @see TestScenarioConfigBeanToRequestScopedMapper
 */
@EnableTestBeans
class Scenario32Test {

    @Test
    void higherPriorityMapperOverridesBuiltInRemap() {
        BeanManager beanManager = CDI.current().getBeanManager();
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(MyConfig.class));

        assertThat(bean)
                .as("CDI bean for the @ConfigBean class must be resolvable")
                .isNotNull();
        assertThat(bean.getScope())
                .as("the higher-@Priority provider's target scope must win the shared "
                        + "@ConfigBean trigger over the built-in @TestClassScoped remap")
                .isEqualTo(RequestScoped.class)
                .isNotEqualTo(TestClassScoped.class);
    }

    @ConfigBean
    public static class MyConfig {

        public String value() {
            return "scenario-32";
        }
    }
}
