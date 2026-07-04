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
import jakarta.enterprise.inject.spi.Extension;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.scope.impl.adapter.context.TestClassScopeStore;
import org.os890.jawelte.module.scope.impl.adapter.context.TestClassScopedContext;
import org.os890.jawelte.module.scope.impl.adapter.context.TestMethodScopeStore;
import org.os890.jawelte.module.scope.impl.adapter.context.TestMethodScopedContext;

/**
 * CDI Extension shipped by scope-module. Single responsibility:
 * during {@code AfterBeanDiscovery}, construct one
 * {@link TestMethodScopeStore} and one {@link TestClassScopeStore},
 * bind both on {@link TestContext} (so the lifecycle adapter and
 * any introspection consumer can reach them), construct the
 * matching {@code Context} impls passing the stores in, and
 * register both contexts via
 * {@link AfterBeanDiscovery#addContext(jakarta.enterprise.context.spi.Context)}.
 *
 * <p>The previous cross-module scope-binding records that lived
 * here (the {@code TestBeanDefaultScope} and
 * {@code AutoMockDefaultScope} {@code TestContext} metadata) are
 * gone — consumer modules now resolve their default scope via
 * the {@code BeanScopeMapper} SPI in {@code core/api/port} (for
 * {@code @TestBean} static fields and producer methods) or via
 * MP Config keys backed by this module's
 * {@code microprofile-config.properties} (for auto-mock defaults
 * and other consumer-owned settings).
 *
 * <p>Stateless — no instance fields. Discovered by the CDI runtime
 * via the
 * {@code META-INF/services/jakarta.enterprise.inject.spi.Extension}
 * registration shipped in this module. Re-instantiated per
 * {@code SeContainer}, so each test class ends up with a fresh
 * pair of stores and contexts.
 *
 * <p>If no {@link TestContext} is active on the current thread
 * (e.g. the user runs the CDI container outside jawelte's
 * bootstrap window via
 * {@code @EnableTestBeans(manageContainer=false)}), the Extension
 * becomes a no-op: the contexts are not registered. The user owns
 * the test scope lifecycle in that case.
 */
public class TestScopeCdiExtension implements Extension {

    /** No-arg constructor required by the CDI runtime. */
    public TestScopeCdiExtension() {
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
        // Bind the method context on TestContext as well as registering it with
        // CDI. ScopeLifecycleAdapter drives activate()/deactivate() through this
        // metadata handle rather than beanManager.getContext(TestMethodScoped),
        // which would throw ContextNotActiveException while the context is
        // inactive (its store unallocated) — see the adapter for details.
        TestMethodScopedContext methodContext = new TestMethodScopedContext(methodStore);
        active.bindMetadata(TestMethodScopedContext.class, methodContext);
        event.addContext(methodContext);
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
