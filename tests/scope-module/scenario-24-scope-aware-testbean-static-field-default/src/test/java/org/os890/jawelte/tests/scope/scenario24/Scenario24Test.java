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
package org.os890.jawelte.tests.scope.scenario24;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.TestBean;
import org.os890.jawelte.module.scope.api.TestClassScoped;

@EnableTestBeans
class Scenario24Test {

    @TestBean
    public static final Greeting GREETING = new Greeting("hello-from-static-field");

    @Inject
    BeanManager beanManager;

    @Inject
    Greeting greeting;

    @Test
    void staticFieldSyntheticBeanDefaultsToTestClassScopedWhenScopeModuleIsPresent() {
        // The @TestBean field has no CDI scope annotation. With
        // scope-module on the classpath, the addendum override kicks in:
        // TestBeanDefaultScope is bound by TestScopeCdiExtension during
        // BeforeBeanDiscovery; cdi-module reads it and registers the
        // synthetic bean as @TestClassScoped instead of @Singleton.
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(Greeting.class));
        assertThat(bean).isNotNull();
        assertThat(bean.getScope()).isEqualTo(TestClassScoped.class);

        // The injected proxy still resolves to the static-field instance
        // (proxy delegates to the field value via the produceWith
        // lambda).
        assertThat(greeting).isNotNull();
        assertThat(greeting.text()).isEqualTo("hello-from-static-field");
    }

    public static class Greeting {

        private final String text;

        // CDI normal-scope proxy needs a non-private no-arg
        // constructor to instantiate the generated subclass.
        protected Greeting() {
            this.text = "<unset>";
        }

        Greeting(String text) {
            this.text = text;
        }

        public String text() {
            return this.text;
        }
    }
}
