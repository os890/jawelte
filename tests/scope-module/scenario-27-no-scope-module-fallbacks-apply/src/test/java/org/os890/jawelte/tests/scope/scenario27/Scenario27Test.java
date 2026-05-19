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
package org.os890.jawelte.tests.scope.scenario27;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.TestBean;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Without scope-module on the classpath the override records
 * (TestBeanDefaultScope, AutoMockDefaultScope) stay unbound, so
 * cdi-module's TICKET-003 defaults apply unchanged: @TestBean
 * static-field synthetic beans are @Singleton, auto-mocks are
 * @RequestScoped.
 *
 * <p>This scenario uses tests/cdi-module/pom.xml as its parent so it
 * inherits cdi-module test deps but no scope-module artifacts.
 */
@EnableTestBeans
@QuarkusTest
class Scenario27Test {

    @TestBean
    public static final Greeting GREETING = new Greeting("hello-with-no-scope-module");

    @Inject
    BeanManager beanManager;

    @Inject
    Greeting greeting;

    @Inject
    UnsatisfiedService autoMock;

    @Test
    void testBeanStaticFieldDefaultsToSingleton() {
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(Greeting.class));
        assertThat(bean).isNotNull();
        assertThat(bean.getScope()).isEqualTo(Singleton.class);
        assertThat(greeting.text()).isEqualTo("hello-with-no-scope-module");
    }

    @Test
    void autoMockDefaultsToRequestScoped() {
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(UnsatisfiedService.class));
        assertThat(bean).isNotNull();
        assertThat(bean.getScope()).isEqualTo(RequestScoped.class);
        assertThat(autoMock).isNotNull();
    }

    public static class Greeting {

        private final String text;

        Greeting(String text) {
            this.text = text;
        }

        public String text() {
            return this.text;
        }
    }
}
