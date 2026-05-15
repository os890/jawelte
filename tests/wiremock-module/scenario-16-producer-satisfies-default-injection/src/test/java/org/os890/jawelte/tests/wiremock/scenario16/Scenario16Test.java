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
package org.os890.jawelte.tests.wiremock.scenario16;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.wiremock.api.EnableWireMock;
import org.os890.jawelte.module.wiremock.impl.WireMockProducer;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Scenario 16 — in default-only mode (no {@code @WireMockEndpoint}
 * qualifier anywhere), the bean satisfying {@code @Inject WireMockServer}
 * comes from {@link WireMockProducer}'s {@code @Default @Produces}
 * method. Verified via {@link BeanManager#getBeans(java.lang.reflect.Type,
 * java.lang.annotation.Annotation...)}: the resolved bean's
 * {@link Bean#getBeanClass()} returns {@code WireMockProducer.class}
 * (CDI sets {@code getBeanClass()} to the producer-method's owning
 * class for producer-method beans).
 *
 * <p>Implicitly verifies the CDI extension did <em>not</em> veto
 * the producer (the veto fires only when at least one
 * {@code @WireMockEndpoint} qualifier is discovered) and did
 * <em>not</em> register synthetic beans (the discovered map is
 * empty in default-only mode).
 */
@EnableWireMock
class Scenario16Test {

    @Inject
    private BeanManager beanManager;

    @Test
    void producerSatisfiesDefaultInjection() {
        var beans = beanManager.getBeans(WireMockServer.class);
        assertThat(beans)
                .as("exactly one WireMockServer bean is visible in default-only mode")
                .hasSize(1);
        Bean<?> wireMockServerBean = beans.iterator().next();
        assertThat(wireMockServerBean.getBeanClass())
                .as("the producer-method bean's getBeanClass() points at WireMockProducer")
                .isEqualTo(WireMockProducer.class);
    }
}
