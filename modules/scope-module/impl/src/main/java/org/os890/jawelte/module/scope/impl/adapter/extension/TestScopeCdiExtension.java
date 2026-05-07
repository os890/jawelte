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
package org.os890.jawelte.module.scope.impl.adapter.extension;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;

import org.os890.jawelte.core.api.port.ScopeBinding;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.scope.api.TestClassScoped;
import org.os890.jawelte.module.scope.api.TestMethodScoped;
import org.os890.jawelte.module.scope.impl.adapter.context.TestClassScopeStore;
import org.os890.jawelte.module.scope.impl.adapter.context.TestClassScopedContext;
import org.os890.jawelte.module.scope.impl.adapter.context.TestMethodScopeStore;
import org.os890.jawelte.module.scope.impl.adapter.context.TestMethodScopedContext;

/**
 * CDI Extension shipped by scope-module. Two responsibilities:
 *
 * <ul>
 *   <li><strong>{@code BeforeBeanDiscovery}</strong>: bind the
 *       cross-module override records
 *       {@link ScopeBinding.TestBeanDefaultScope} (value
 *       {@link TestClassScoped TestClassScoped.class}) and
 *       {@link ScopeBinding.AutoMockDefaultScope} (value
 *       {@link TestMethodScoped TestMethodScoped.class}) on the
 *       active {@link TestContext}. cdi-module's CDI Extension
 *       reads these in {@code AfterBeanDiscovery} when synthesising
 *       {@code @TestBean} static-field beans and auto-mocks (per
 *       the TICKET-003 addendum).</li>
 *   <li><strong>{@code AfterBeanDiscovery}</strong>: construct one
 *       {@link TestMethodScopeStore} and one {@link TestClassScopeStore},
 *       bind both on {@code TestContext} (so the lifecycle adapter
 *       and any introspection consumer can reach them), construct
 *       the matching {@code Context} impls passing the stores in,
 *       and register both contexts via
 *       {@link AfterBeanDiscovery#addContext(jakarta.enterprise.context.spi.Context)}.</li>
 * </ul>
 *
 * <p>Stateless — no instance fields. Discovered by the CDI runtime
 * via the
 * {@code META-INF/services/jakarta.enterprise.inject.spi.Extension}
 * registration shipped in this module. Re-instantiated per
 * {@code SeContainer}, so each test class ends up with a fresh pair
 * of stores and contexts.
 *
 * <p>If no {@link TestContext} is active on the current thread (e.g.
 * the user runs the CDI container outside jawelte's bootstrap window
 * via {@code @EnableTestBeans(manageContainer=false)}), the
 * Extension becomes a no-op: the two metadata records stay unbound
 * and the contexts are not registered. The user owns the test scope
 * lifecycle in that case.
 */
public class TestScopeCdiExtension implements Extension {

    /** No-arg constructor required by the CDI runtime. */
    public TestScopeCdiExtension() {
    }

    void onBeforeBeanDiscovery(@Observes BeforeBeanDiscovery event) {
        TestContext active = activeContextOrNull();
        if (active == null) {
            return;
        }
        active.bindMetadata(
                ScopeBinding.TestBeanDefaultScope.class,
                new ScopeBinding.TestBeanDefaultScope(TestClassScoped.class));
        active.bindMetadata(
                ScopeBinding.AutoMockDefaultScope.class,
                new ScopeBinding.AutoMockDefaultScope(TestMethodScoped.class));
    }

    void onAfterBeanDiscovery(@Observes AfterBeanDiscovery event) {
        TestContext active = activeContextOrNull();
        if (active == null) {
            return;
        }
        TestMethodScopeStore methodStore = new TestMethodScopeStore();
        TestClassScopeStore classStore = new TestClassScopeStore();
        active.bindMetadata(TestMethodScopeStore.class, methodStore);
        active.bindMetadata(TestClassScopeStore.class, classStore);
        event.addContext(new TestMethodScopedContext(methodStore));
        event.addContext(new TestClassScopedContext(classStore));
    }

    private static TestContext activeContextOrNull() {
        try {
            return TestContext.get();
        } catch (IllegalStateException noActiveContext) {
            return null;
        }
    }
}
