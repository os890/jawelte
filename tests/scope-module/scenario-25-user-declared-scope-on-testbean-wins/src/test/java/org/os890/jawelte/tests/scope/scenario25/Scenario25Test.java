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
package org.os890.jawelte.tests.scope.scenario25;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.TestBean;


import io.quarkus.test.junit.QuarkusTest;
@EnableTestBeans
@QuarkusTest
class Scenario25Test {

    // The user explicitly annotates the static field with
    // @RequestScoped. Per the addendum precedence, user-declared
    // CDI scope wins over scope-module's TestBeanDefaultScope.
    @RequestScoped
    @TestBean
    public static final Greeting GREETING = new Greeting("hello-from-request-scoped-field");

    @Inject
    BeanManager beanManager;

    @Test
    void userDeclaredCdiScopeOverridesScopeModuleDefault() {
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(Greeting.class));
        assertThat(bean).isNotNull();
        assertThat(bean.getScope())
                .as("user-declared @RequestScoped on the static field wins over the scope-module default")
                .isEqualTo(RequestScoped.class);
    }

    public static class Greeting {

        private final String text;

        // CDI normal-scope proxy (@RequestScoped) needs a non-private
        // no-arg constructor to instantiate the generated subclass.
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
