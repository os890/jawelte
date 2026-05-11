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
package org.os890.jawelte.tests.ejb.scenario27;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * TICKET-007 scenario 27 — when the effective mapper chain is a
 * no-op (the only registered terminal returns {@code null} for
 * everything; the shipping default is sorted out), the extension
 * still surfaces EJB-annotated classes as CDI beans (no NPE, no
 * failed bootstrap). Empirically: neither OpenWebBeans nor Weld
 * propagates {@code addStereotype}-implied member annotations
 * (the {@code @ApplicationScoped} the extension declared on the
 * stereotype) to types registered via {@code addAnnotatedType} —
 * the resolved scope falls back to CDI's no-scope default
 * {@code @Dependent}. The assertion captures the achievable
 * behaviour: bean present, resolvable, no crash.
 */
@EnableTestBeans
class Scenario27Test {

    @Inject
    BeanManager beanManager;

    @Test
    void emptyChainStillSurfacesEjbBeans() {
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(UntouchedSingleton.class));
        assertThat(bean).isNotNull();
        assertThat(bean.getBeanClass()).isEqualTo(UntouchedSingleton.class);
    }
}
