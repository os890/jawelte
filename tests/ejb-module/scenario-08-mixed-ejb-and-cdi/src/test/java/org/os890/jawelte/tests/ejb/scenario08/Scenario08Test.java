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
package org.os890.jawelte.tests.ejb.scenario08;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * TICKET-007 scenario 8 — EJB and CDI beans coexist in the same
 * container. ejb-module rewrites the {@code @Singleton} bean's
 * metadata; the plain {@code @ApplicationScoped} CDI bean is
 * untouched. Both are injectable side by side.
 */
@EnableTestBeans
class Scenario08Test {

    @Inject
    EjbSingletonBean ejb;

    @Inject
    CdiApplicationScopedBean cdi;

    @Test
    void ejbAndCdiBeansCoexist() {
        assertThat(ejb).isNotNull();
        assertThat(cdi).isNotNull();
        assertThat(ejb.tag()).isEqualTo("ejb");
        assertThat(cdi.tag()).isEqualTo("cdi");
    }
}
