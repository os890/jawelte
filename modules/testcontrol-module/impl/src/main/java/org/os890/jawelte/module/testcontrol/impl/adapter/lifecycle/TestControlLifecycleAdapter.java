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
package org.os890.jawelte.module.testcontrol.impl.adapter.lifecycle;

import java.lang.reflect.Method;
import java.util.Optional;

import jakarta.annotation.Priority;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;
import org.os890.jawelte.module.testcontrol.api.TestControl;

/**
 * {@link TestModuleLifecyclePort} adapter shipped by
 * testcontrol-module/impl. Resolves the active test method's
 * {@link TestControl} annotation in {@code beforeEach} and publishes
 * it on {@link TestContext} so the Phase&nbsp;4 / Phase&nbsp;5
 * collaborators (the {@code BeforeScopeStarted} observer and the
 * {@code AfterTestTransaction} observer) can read it through
 * {@link TestContext#getMetadata(Class)} with the key
 * {@code TestControl.class}.
 *
 * <p><b>Priority.</b> {@code @Priority(50)} — the lowest among the
 * jawelte modules' lifecycle adapters, so {@code beforeAll} /
 * {@code beforeEach} run <em>first</em> in the chain (before
 * scope-module at 100 fires {@code BeforeScopeStarted}, and before
 * jpa-module at 200 opens a transaction). {@code afterAll} /
 * {@code afterEach} run <em>last</em> (LIFO) so this adapter sees the
 * post-test state — used by the non-transactional {@code dbExpected/}
 * fallback path added in Phase&nbsp;5.
 *
 * <p><b>Annotation lookup.</b> Uses JUnit Jupiter's
 * {@link AnnotationSupport#findAnnotation(java.lang.reflect.AnnotatedElement,
 * Class) AnnotationSupport.findAnnotation} on the active test method
 * (read off the per-method {@link ExtensionContext} that
 * {@code core/impl}'s {@code DelegatingJUnitExtension} bound on
 * {@code TestContext} in {@code beforeEach}). The Jupiter platform
 * walks the test class hierarchy when the method is not overridden,
 * which is the inheritance contract documented on
 * {@link TestControl}.
 *
 * <p><b>State.</b> Stateless — no instance fields. All per-method
 * state lives on {@link TestContext} via {@code bindMetadata} /
 * {@code unbindMetadata(TestControl.class)}.
 *
 * <p><b>Phase&nbsp;3 (this commit) responsibility.</b> Resolution and
 * publication only. The scope-veto wiring
 * ({@code TestControlScopeObserver#configureAllowedScopes}) and the
 * test-data orchestration ({@code TestDataHandler.seedAll} / the
 * {@code AfterTestTransaction} observer / the non-transactional
 * {@code dbExpected/} fallback) are added in subsequent phases that
 * extend this adapter's {@code beforeEach} / {@code afterEach}.
 */
@Priority(50)
public class TestControlLifecycleAdapter implements TestModuleLifecyclePort {

    /** No-arg constructor used by {@code ServiceLoader}. */
    public TestControlLifecycleAdapter() {
    }

    @Override
    public void beforeEach(TestContext testContext) {
        Optional<ExtensionContext> junitContext = testContext.getMetadata(ExtensionContext.class);
        if (junitContext.isEmpty()) {
            return;
        }
        Optional<Method> testMethod = junitContext.get().getTestMethod();
        if (testMethod.isEmpty()) {
            return;
        }
        AnnotationSupport.findAnnotation(testMethod.get(), TestControl.class)
                .ifPresent(annotation -> testContext.bindMetadata(TestControl.class, annotation));
    }

    @Override
    public void afterEach(TestContext testContext) {
        testContext.unbindMetadata(TestControl.class);
    }
}
