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
package org.os890.jawelte.tests.cdi.scenario56;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.apache.deltaspike.integration.DeltaSpikeStubBean;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

@EnableTestBeans(limitToTestBeans = true)
class Scenario56Test {

    @Inject
    BeanManager beanManager;

    @Test
    void deltaSpikeInternalBeanSurvivesWhitelistMode() {
        // DeltaSpikeStubBean lives under org.apache.deltaspike. , a
        // prefix the cdi-module's bundled MP Config defaults add to
        // the framework allowlist. With limitToTestBeans=true and no
        // explicit @TestBean for it, the bean must still survive the
        // veto pass and remain resolvable.
        Set<Bean<?>> beans = beanManager.getBeans(DeltaSpikeStubBean.class);
        assertThat(beans).hasSize(1);
    }
}
