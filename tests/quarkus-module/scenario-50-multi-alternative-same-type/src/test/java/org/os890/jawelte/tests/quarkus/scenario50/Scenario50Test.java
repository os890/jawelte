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
package org.os890.jawelte.tests.quarkus.scenario50;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.TestBean;

@EnableTestBeans
@TestBean(bean = GreetingA.class)
class Scenario50Test {

    @Inject
    Greeting greeting;

    @Inject
    BeanManager beanManager;

    @Test
    void selectedAlternativeWinsAndUnselectedIsInactive() {
        // GreetingA is the @TestBean-selected alternative; GreetingB is
        // also on the classpath as @Alternative but is not selected.
        // CDI must resolve Greeting unambiguously to GreetingA.
        assertThat(greeting).isNotNull();
        assertThat(greeting.greet("hello")).isEqualTo("A:hello");

        Set<Bean<?>> beans = beanManager.getBeans(Greeting.class);
        assertThat(beans).hasSize(1);
        assertThat(beans.iterator().next().getBeanClass()).isEqualTo(GreetingA.class);
    }
}
