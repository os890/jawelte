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
package org.os890.jawelte.tests.quarkus.scenario21;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@EnableTestBeans
class Scenario21Test {

    @Inject
    BeanManager beanManager;

    @Inject
    @ConfigKey(name = "app.name")
    List<String> tags;

    @Test
    void jdkTypedAutoMockUsesDependentScopeAndCarriesQualifier() {
        assertThat(tags).isNotNull();

        ConfigKey qualifier = new ConfigKeyLiteral("app.name");
        Type parameterizedListOfString = new TypeLiteral<List<String>>() { }.getType();
        Set<Bean<?>> beans = beanManager.getBeans(parameterizedListOfString, qualifier);
        assertThat(beans).hasSize(1);
        assertThat(beans.iterator().next().getScope()).isEqualTo(Dependent.class);
    }

    private static class ConfigKeyLiteral
            extends jakarta.enterprise.util.AnnotationLiteral<ConfigKey>
            implements ConfigKey {

        private static final long serialVersionUID = 1L;
        private final String name;

        ConfigKeyLiteral(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }
    }
}
