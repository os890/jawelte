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
package org.os890.jawelte.module.scope.impl.adapter.context;

/**
 * Static handoff slot for the per-test-class {@code @TestMethodScoped}
 * and {@code @TestClassScoped} bean stores. Populated by
 * {@code ScopeArcContextContributor} during cdi-module's
 * {@code CdiTestBeanContainer.beforeAll}, just before
 * {@code BeanProcessor.process()} fires the registered
 * {@code ContextRegistrar}s — the {@code ContextCreator}s ArC
 * subsequently invokes (during {@code Arc.initialize}) read both
 * stores back from here and pass them into the freshly-constructed
 * {@code TestClassScopedContext} / {@code TestMethodScopedContext}.
 *
 * <p>Why a static singleton and not a {@code ThreadLocal}: ArC's
 * {@code ContextCreator.create(Map)} accepts only typed parameters
 * (Class, int, long, double, String, boolean), so we cannot pass live
 * store references through the {@code ContextConfigurator.param(...)}
 * channel. The CDI container is bootstrapped sequentially per test
 * class (the previous {@code Arc.shutdown()} runs first in
 * {@code beforeAll}), so the static fields are guaranteed to hold the
 * right pair when the creators run.
 *
 * <p>Single-test-class-at-a-time per JVM is also the
 * existing assumption that the rest of cdi-module's bootstrap relies
 * on. Concurrent boots of different test classes within a single JVM
 * are not supported.
 */
public abstract class TestScopeCurrentStores {

    private static volatile TestMethodScopeStore methodStore;
    private static volatile TestClassScopeStore classStore;

    /**
     * Suppressed-instantiation constructor — the class is a pure
     * static holder.
     */
    protected TestScopeCurrentStores() {
    }

    /**
     * Publish the freshly-built store pair so the corresponding
     * {@code ContextCreator}s can read them on their first invocation.
     *
     * @param methodStoreToSet the method-scope store for the current
     *                         test class
     * @param classStoreToSet  the class-scope store for the current
     *                         test class
     */
    public static void set(
            TestMethodScopeStore methodStoreToSet, TestClassScopeStore classStoreToSet) {
        methodStore = methodStoreToSet;
        classStore = classStoreToSet;
    }

    /**
     * The most-recently-published method-scope store. When called
     * before any {@link #set(TestMethodScopeStore, TestClassScopeStore)}
     * — which is the case under {@code @QuarkusTest}, where no
     * contributor binds the stores up front — a fresh store is
     * allocated lazily so the matching {@code ContextCreator} can
     * still build a working {@code TestMethodScopedContext}.
     *
     * @return the current store; never {@code null}
     */
    public static synchronized TestMethodScopeStore methodStore() {
        if (methodStore == null) {
            methodStore = new TestMethodScopeStore();
        }
        return methodStore;
    }

    /**
     * The most-recently-published class-scope store. When called
     * before any {@link #set(TestMethodScopeStore, TestClassScopeStore)}
     * — which is the case under {@code @QuarkusTest}, where no
     * contributor binds the stores up front — a fresh store is
     * allocated lazily so the matching {@code ContextCreator} can
     * still build a working {@code TestClassScopedContext}.
     *
     * @return the current store; never {@code null}
     */
    public static synchronized TestClassScopeStore classStore() {
        if (classStore == null) {
            classStore = new TestClassScopeStore();
        }
        return classStore;
    }

    /**
     * Reset both store slots — used by integrations that drive the
     * per-test-class lifecycle externally and want a clean handoff
     * to the next class. The default scope-lifecycle adapter calls
     * this in its {@code afterAll}.
     */
    public static synchronized void reset() {
        methodStore = null;
        classStore = null;
    }
}
