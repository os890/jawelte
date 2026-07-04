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
package org.os890.jawelte.module.scope.impl.adapter.lifecycle;

import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.spi.BeanManager;

import org.os890.jawelte.core.api.event.BeforeScopeStarted;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;
import org.os890.jawelte.module.scope.api.TestClassScoped;
import org.os890.jawelte.module.scope.api.TestMethodScoped;
import org.os890.jawelte.module.scope.impl.adapter.context.TestClassScopedContext;
import org.os890.jawelte.module.scope.impl.adapter.context.TestMethodScopedContext;

/**
 * {@link TestModuleLifecyclePort} adapter shipped by scope-module.
 * Drives activation / deactivation of the test-method-scoped and
 * test-class-scoped CDI contexts registered by
 * {@link org.os890.jawelte.module.scope.impl.adapter.extension.TestScopeCdiExtension}.
 *
 * <p>Stateless — no instance fields. The same adapter instance is
 * reused across every test class running in the JVM (per the
 * project rule "all per-test-class state on TestContext"); the
 * stores and the {@code SeContainer} live on {@code TestContext}
 * and are reached lazily through {@link TestContext#getMetadata(Class)}.
 *
 * <p>{@code @Priority(100)} puts the adapter early in the
 * {@link TestModuleLifecyclePort} chain — after framework-wide
 * policy modules ({@code @Priority < 100}) and before higher-numbered
 * modules that need active scopes for their own setup. {@code afterEach}
 * / {@code afterAll} run in reverse (LIFO) order, so
 * the adapter deactivates after the higher-numbered modules tear down.
 *
 * <p>The {@code BeforeScopeStarted(TestMethodScoped.class)} event
 * fired in {@link #beforeEach(TestContext)} is observable for any
 * CDI observer in the chain, and its veto status <strong>is</strong>
 * honored: if an observer vetoes it (e.g. testcontrol's
 * {@code TestControlScopeObserver}, driven by
 * {@code @TestControl(startScopes=…)}), the adapter leaves the
 * {@code @TestMethodScoped} store unallocated, so the scope stays
 * inactive and {@code @TestMethodScoped} bean access throws
 * {@code ContextNotActiveException} for that method.
 *
 * <p>{@code @TestClassScoped} has a class lifetime (its store is
 * allocated once at {@code AfterBeanDiscovery} and torn down in
 * {@code afterAll}); no per-method {@code BeforeScopeStarted} event is
 * fired for it, so it cannot be vetoed per method.
 *
 * <p>If the CDI container was not booted by jawelte (e.g.
 * {@code @EnableTestBeans(manageContainer=false)} where the user
 * boots a container that does not load {@code TestScopeCdiExtension}),
 * the contexts are never registered. The adapter then silently
 * no-ops: the stores are absent from {@code TestContext} and the
 * adapter has nothing to drive.
 */
@Priority(100)
public class ScopeLifecycleAdapter implements TestModuleLifecyclePort {

    /** No-arg constructor used by {@code ServiceLoader}. */
    public ScopeLifecycleAdapter() {
    }

    @Override
    public void beforeAll(TestContext testContext) {
        // No-op for scope-module's contexts:
        //  - TestClassScopedContext is already active (its store was
        //    allocated in its constructor during AfterBeanDiscovery).
        //  - TestMethodScopedContext is activated per beforeEach.
    }

    @Override
    public void beforeEach(TestContext testContext) {
        Optional<BeanManager> beanManager = beanManagerFor(testContext);
        if (beanManager.isEmpty()) {
            return;
        }
        boolean vetoed = fireBeforeScopeStarted(beanManager.get(), TestMethodScoped.class);
        if (vetoed) {
            // Honor the BeforeScopeStarted veto: when an observer (e.g.
            // testcontrol's TestControlScopeObserver, driven by
            // @TestControl(startScopes=…)) vetoes @TestMethodScoped, skip
            // activation. TestMethodScopedContext.isActive() is then false and
            // @TestMethodScoped bean access throws ContextNotActiveException for
            // this method — exactly what the BeforeScopeStarted contract promises.
            return;
        }
        // Look the context up from TestContext (TestScopeCdiExtension binds it),
        // not via beanManager.getContext(TestMethodScoped) — the latter throws
        // ContextNotActiveException while the context is inactive (its store
        // unallocated), a chicken-and-egg that would block the very activation
        // performed here.
        testContext.getMetadata(TestMethodScopedContext.class).ifPresent(TestMethodScopedContext::activate);
    }

    @Override
    public void afterEach(TestContext testContext) {
        // Deactivate through the context (see beforeEach for why it is looked up
        // from TestContext rather than via beanManager.getContext).
        testContext.getMetadata(TestMethodScopedContext.class).ifPresent(TestMethodScopedContext::deactivate);
    }

    @Override
    public void afterAll(TestContext testContext) {
        Optional<BeanManager> beanManager = beanManagerFor(testContext);
        if (beanManager.isEmpty()) {
            return;
        }
        TestClassScopedContext classContext = classScopedContext(beanManager.get());
        if (classContext != null) {
            classContext.deactivate();
        }
    }

    private static Optional<BeanManager> beanManagerFor(TestContext testContext) {
        return testContext.getMetadata(SeContainer.class).map(SeContainer::getBeanManager);
    }

    private static boolean fireBeforeScopeStarted(
            BeanManager beanManager, Class<? extends java.lang.annotation.Annotation> scope) {
        try {
            BeforeScopeStarted event = new BeforeScopeStarted(scope);
            beanManager.getEvent().fire(event);
            return event.isVetoed();
        } catch (RuntimeException ignored) {
            // Per TICKET-004: a CDI runtime in an unexpected state during
            // event-firing is treated as "not vetoed" — the caller activates
            // the scope; any subsequent failure propagates per TICKET-001.
            return false;
        }
    }

    private static TestClassScopedContext classScopedContext(BeanManager beanManager) {
        try {
            return (TestClassScopedContext) beanManager.getContext(TestClassScoped.class);
        } catch (RuntimeException missingContext) {
            return null;
        }
    }
}
