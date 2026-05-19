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
package org.os890.jawelte.module.scope.impl.adapter.contributor;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.impl.spi.ArcContextContributor;
import org.os890.jawelte.module.scope.api.TestClassScoped;
import org.os890.jawelte.module.scope.api.TestMethodScoped;
import org.os890.jawelte.module.scope.impl.adapter.context.TestClassScopeContextCreator;
import org.os890.jawelte.module.scope.impl.adapter.context.TestClassScopeStore;
import org.os890.jawelte.module.scope.impl.adapter.context.TestMethodScopeContextCreator;
import org.os890.jawelte.module.scope.impl.adapter.context.TestMethodScopeStore;
import org.os890.jawelte.module.scope.impl.adapter.context.TestScopeCurrentStores;

import io.quarkus.arc.processor.BeanProcessor;

/**
 * scope-module's {@link ArcContextContributor}: at
 * {@code CdiTestBeanContainer.beforeAll}, builds the per-test-class
 * bean stores for {@code @TestClassScoped} and {@code @TestMethodScoped},
 * binds them on {@link TestContext} so the matching
 * {@code ScopeLifecycleAdapter} can drive activation / deactivation,
 * stashes them in {@link TestScopeCurrentStores} for the ArC
 * {@code ContextCreator}s to read, and registers both scopes as
 * normal scopes with ArC via a {@code ContextRegistrar} on the
 * supplied {@link BeanProcessor.Builder}.
 *
 * <p>Discovered via
 * {@code META-INF/services/org.os890.jawelte.module.cdi.impl.spi.ArcContextContributor}.
 *
 * <p>Replaces the previous OWB / Weld {@code TestScopeCdiExtension}
 * which used the CDI portable extension SPI
 * ({@code @Observes AfterBeanDiscovery#addContext(...)}); ArC has no
 * runtime equivalent for that callback so the registration is moved
 * to ArC's build-time {@code ContextRegistrar} surface, called from
 * cdi-module/impl just before {@code BeanProcessor.process()}.
 */
public class ScopeArcContextContributor implements ArcContextContributor {

    /** No-arg constructor required by {@code ServiceLoader}. */
    public ScopeArcContextContributor() {
    }

    @Override
    public void contribute(TestContext testContext, BeanProcessor.Builder builder) {
        TestMethodScopeStore methodStore = new TestMethodScopeStore();
        TestClassScopeStore classStore = new TestClassScopeStore();
        testContext.bindMetadata(TestMethodScopeStore.class, methodStore);
        testContext.bindMetadata(TestClassScopeStore.class, classStore);
        TestScopeCurrentStores.set(methodStore, classStore);

        builder.addContextRegistrar(registration -> {
            registration.configure(TestClassScoped.class)
                    .normal()
                    .creator(TestClassScopeContextCreator.class)
                    .done();
            registration.configure(TestMethodScoped.class)
                    .normal()
                    .creator(TestMethodScopeContextCreator.class)
                    .done();
        });
    }
}
