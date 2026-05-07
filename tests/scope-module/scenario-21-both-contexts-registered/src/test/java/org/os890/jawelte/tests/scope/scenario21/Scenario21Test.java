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
package org.os890.jawelte.tests.scope.scenario21;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.scope.api.TestClassScoped;
import org.os890.jawelte.module.scope.api.TestMethodScoped;
import org.os890.jawelte.module.scope.impl.adapter.context.TestClassScopedContext;
import org.os890.jawelte.module.scope.impl.adapter.context.TestMethodScopedContext;

@EnableTestBeans
class Scenario21Test {

    @Inject
    BeanManager beanManager;

    @Test
    void bothScopeContextsAreRegisteredAndAreTheConcreteImplsFromScopeModule() {
        Context methodContext = beanManager.getContext(TestMethodScoped.class);
        Context classContext = beanManager.getContext(TestClassScoped.class);

        // Both contexts come from scope-module's TestScopeCdiExtension
        // via AfterBeanDiscovery.addContext(...).
        assertThat(methodContext).isInstanceOf(TestMethodScopedContext.class);
        assertThat(classContext).isInstanceOf(TestClassScopedContext.class);
        assertThat(methodContext.isActive()).isTrue();
        assertThat(classContext.isActive()).isTrue();
    }
}
