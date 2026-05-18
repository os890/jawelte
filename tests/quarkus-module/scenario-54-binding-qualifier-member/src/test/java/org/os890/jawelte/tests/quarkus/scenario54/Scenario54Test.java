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
package org.os890.jawelte.tests.quarkus.scenario54;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

@EnableTestBeans
class Scenario54Test {

    @Inject
    @ServiceType("express")
    Cache express;

    @Inject
    @ServiceType("standard")
    Cache standard;

    @Inject
    BeanManager beanManager;

    @Test
    void bindingQualifierMemberValuesProduceDistinctSyntheticBeans() {
        assertThat(express).isNotNull();
        assertThat(standard).isNotNull();

        // value() is a binding member (no @Nonbinding) so the two
        // qualifier instances must resolve to two different beans.
        Set<Bean<?>> expressBeans = beanManager.getBeans(Cache.class, new ServiceTypeLiteral("express"));
        Set<Bean<?>> standardBeans = beanManager.getBeans(Cache.class, new ServiceTypeLiteral("standard"));

        assertThat(expressBeans).hasSize(1);
        assertThat(standardBeans).hasSize(1);
        assertThat(expressBeans.iterator().next())
                .isNotEqualTo(standardBeans.iterator().next());
    }

    private static class ServiceTypeLiteral
            extends AnnotationLiteral<ServiceType> implements ServiceType {

        private static final long serialVersionUID = 1L;
        private final String value;

        ServiceTypeLiteral(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }
}
