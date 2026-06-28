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
package org.os890.jawelte.tests.cdi.scenario59;

import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Subject driven twice by {@link Scenario59Test} (once per container).
 * The unsatisfied {@link AutoMockScopeProbe} injection point is
 * auto-mocked at the non-JDK auto-mock scope configured for THIS
 * container; the test records the auto-mock bean's actual scope so the
 * driver can assert each container saw its own configured scope.
 */
@EnableTestBeans
public class AutoMockScopeSubject {

    @Inject
    private AutoMockScopeProbe probe;

    @Inject
    private BeanManager beanManager;

    public AutoMockScopeSubject() {
    }

    @Test
    void recordsTheAutoMockScopeForThisContainer() {
        // Touch the mock so the injection is real, then read the scope
        // the auto-mock synthetic bean was registered with.
        probe.ping();
        Class<? extends java.lang.annotation.Annotation> scope =
                beanManager.getBeans(AutoMockScopeProbe.class).iterator().next().getScope();
        RecordedScopes.ENTRIES.add(scope.getName());
    }
}
