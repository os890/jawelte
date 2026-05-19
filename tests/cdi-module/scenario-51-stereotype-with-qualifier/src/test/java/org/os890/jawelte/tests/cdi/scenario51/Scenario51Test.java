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
package org.os890.jawelte.tests.cdi.scenario51;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;


import io.quarkus.test.junit.QuarkusTest;
@EnableTestBeans
@QuarkusTest
class Scenario51Test {

    @Inject
    @Premium
    HealthCheck healthCheck;

    @Inject
    BeanManager beanManager;

    @Test
    void stereotypeAppliesScopeAndDirectQualifierIsHonored() {
        // MonitoredHealthCheck is annotated @Monitored (stereotype that
        // applies @ApplicationScoped) and @Premium (a qualifier declared
        // directly on the class). The framework must NOT auto-mock the
        // @Premium HealthCheck IP - the stereotype-driven bean
        // satisfies it. (Real bean returns true; a Mockito mock would
        // return false for the isHealthy() boolean.)
        assertThat(healthCheck).isNotNull();
        assertThat(healthCheck.isHealthy()).isTrue();

        Set<Bean<?>> beans = beanManager.getBeans(HealthCheck.class, new PremiumLiteral());
        assertThat(beans).hasSize(1);
        Bean<?> bean = beans.iterator().next();
        assertThat(bean.getBeanClass()).isEqualTo(MonitoredHealthCheck.class);
        assertThat(bean.getScope()).isEqualTo(ApplicationScoped.class);
    }

    private static class PremiumLiteral
            extends AnnotationLiteral<Premium> implements Premium {

        private static final long serialVersionUID = 1L;
    }
}
