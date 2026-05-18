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

import jakarta.annotation.Priority;

import org.os890.jawelte.core.api.event.BeforeScopeStarted;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;
import org.os890.jawelte.module.scope.api.TestMethodScoped;
import org.os890.jawelte.module.scope.impl.adapter.context.TestClassScopeStore;
import org.os890.jawelte.module.scope.impl.adapter.context.TestMethodScopeStore;
import org.os890.jawelte.module.scope.impl.adapter.context.TestScopeCurrentStores;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;

/**
 * {@link TestModuleLifecyclePort} adapter shipped by scope-module.
 * Drives activation / deactivation of the test-method-scoped and
 * test-class-scoped CDI scopes registered by
 * {@code ScopeArcContextContributor} (an {@code ArcContextContributor}
 * consumed by cdi-module's {@code CdiTestBeanContainer} during its ArC
 * bootstrap).
 *
 * <p>Stateless — no instance fields. Reaches the per-test-class
 * stores via {@link TestContext#getMetadata(Class)} (bound by
 * the contributor) and operates on them directly; the
 * {@code Context} impls themselves are thin delegates over the
 * stores, so calling {@code allocate()} / {@code destroyAll()} on
 * the store has the same observable effect as going through the
 * {@code Context} accessors.
 *
 * <p>{@code @Priority(100)} puts the adapter early in the
 * {@link TestModuleLifecyclePort} chain — after framework-wide
 * policy modules ({@code @Priority < 100}) and before higher-numbered
 * modules that need active scopes for their own setup. {@code afterEach}
 * / {@code afterAll} run in reverse (LIFO) order per TICKET-001, so
 * the adapter deactivates after the higher-numbered modules tear down.
 *
 * <p>The {@code BeforeScopeStarted(TestMethodScoped.class)} event
 * fired in {@link #beforeEach(TestContext)} is observable for any
 * CDI observer in the chain via ArC's runtime BeanManager. Its veto
 * status does <strong>not</strong> affect this adapter's behaviour;
 * the adapter calls {@link TestMethodScopeStore#allocate()}
 * unconditionally afterwards.
 *
 * <p>If the stores are absent from {@link TestContext} (e.g. the
 * user booted a container that did not load
 * {@code ScopeArcContextContributor}), each callback silently
 * no-ops.
 */
@Priority(100)
public class ScopeLifecycleAdapter implements TestModuleLifecyclePort {

    /** No-arg constructor used by {@code ServiceLoader}. */
    public ScopeLifecycleAdapter() {
    }

    @Override
    public void beforeAll(TestContext testContext) {
        // No-op: TestClassScopeStore allocates its map in its
        // constructor; TestMethodScopeStore is allocated per
        // beforeEach.
    }

    @Override
    public void beforeEach(TestContext testContext) {
        TestMethodScopeStore methodStore = resolveMethodStore(testContext);
        if (methodStore == null) {
            return;
        }
        fireBeforeScopeStarted(TestMethodScoped.class);
        methodStore.allocate();
    }

    @Override
    public void afterEach(TestContext testContext) {
        TestMethodScopeStore methodStore = resolveMethodStore(testContext);
        if (methodStore != null) {
            methodStore.destroyAll();
        }
    }

    @Override
    public void afterAll(TestContext testContext) {
        TestClassScopeStore classStore = resolveClassStore(testContext);
        if (classStore != null) {
            classStore.destroyAll();
        }
        TestScopeCurrentStores.reset();
    }

    /**
     * Pull the method-scope store from {@link TestContext} metadata
     * (set by the standalone-ArC contributor) first; fall back to
     * the {@link TestScopeCurrentStores} static slot, which the
     * {@code ContextCreator} lazy-initialises under
     * {@code @QuarkusTest} (where no contributor runs).
     */
    private static TestMethodScopeStore resolveMethodStore(TestContext testContext) {
        return testContext.getMetadata(TestMethodScopeStore.class)
                .orElseGet(TestScopeCurrentStores::methodStore);
    }

    private static TestClassScopeStore resolveClassStore(TestContext testContext) {
        return testContext.getMetadata(TestClassScopeStore.class)
                .orElseGet(TestScopeCurrentStores::classStore);
    }

    private static void fireBeforeScopeStarted(Class<? extends java.lang.annotation.Annotation> scope) {
        ArcContainer container = Arc.container();
        if (container == null) {
            return;
        }
        try {
            container.beanManager().getEvent().fire(new BeforeScopeStarted(scope));
        } catch (RuntimeException ignored) {
            // Per TICKET-004: a CDI runtime in an unexpected state during
            // event-firing is treated as "not vetoed". The unconditional
            // allocate() that follows still runs; any subsequent failure
            // propagates per TICKET-001.
        }
    }
}
