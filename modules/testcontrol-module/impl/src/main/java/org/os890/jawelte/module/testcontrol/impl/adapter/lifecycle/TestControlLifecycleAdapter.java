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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;
import org.os890.jawelte.module.testcontrol.api.TestControl;
import org.os890.jawelte.module.testcontrol.impl.adapter.observer.TestControlScopeObserver;

/**
 * {@link TestModuleLifecyclePort} adapter shipped by
 * testcontrol-module/impl. Resolves the active test method's
 * {@link TestControl} annotation in {@code beforeEach}, publishes it
 * on {@link TestContext} (so the Phase&nbsp;5 {@code AfterTestTransaction}
 * observer can read it through {@link TestContext#getMetadata(Class)}
 * with the key {@code TestControl.class}), and pushes the
 * {@code startScopes} allow-list to the {@link TestControlScopeObserver}
 * CDI bean before scope-module's adapter fires its
 * {@code BeforeScopeStarted} events.
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
 * <p><b>Scope-observer wiring.</b> The adapter looks the
 * {@link TestControlScopeObserver} bean up through the
 * {@link BeanManager} of the {@link SeContainer} bound on
 * {@link TestContext}. The observer is reconfigured on every
 * {@code beforeEach} call so residual state from a previous test
 * method cannot influence the current one:
 *
 * <ul>
 *   <li>{@code @TestControl} present with non-empty
 *       {@code startScopes}: push the set of scope classes.</li>
 *   <li>{@code @TestControl} absent, or present with empty
 *       {@code startScopes}: push {@code null} — the observer's
 *       sentinel for "no veto policy active".</li>
 * </ul>
 *
 * <p>If the CDI container was not booted by jawelte (no
 * {@code SeContainer} on {@code TestContext}) or the observer bean is
 * not on the classpath, the wiring is a silent no-op; the observer
 * itself is also a no-op without a configured allow-list.
 *
 * <p><b>State.</b> Stateless — no instance fields. All per-method
 * state lives on {@link TestContext} via {@code bindMetadata} /
 * {@code unbindMetadata(TestControl.class)} and on the observer's own
 * {@code volatile} allow-list field.
 *
 * <p><b>Phase&nbsp;3–4 (this commit) responsibility.</b>
 * {@code @TestControl} resolution + publication and
 * {@code startScopes} push to the scope observer. The test-data
 * orchestration ({@code TestDataHandler.seedAll} / the
 * {@code AfterTestTransaction} observer / the non-transactional
 * {@code dbExpected/} fallback) is added in subsequent phases.
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
        Optional<TestControl> annotation =
                AnnotationSupport.findAnnotation(testMethod.get(), TestControl.class);
        annotation.ifPresent(value -> testContext.bindMetadata(TestControl.class, value));
        configureScopeObserver(testContext, annotation.orElse(null));
    }

    @Override
    public void afterEach(TestContext testContext) {
        testContext.unbindMetadata(TestControl.class);
    }

    private static void configureScopeObserver(TestContext testContext, TestControl annotation) {
        TestControlScopeObserver observer = resolveScopeObserver(testContext);
        if (observer == null) {
            return;
        }
        observer.configureAllowedScopes(toAllowedSet(annotation));
    }

    private static Set<Class<? extends Annotation>> toAllowedSet(TestControl annotation) {
        if (annotation == null || annotation.startScopes().length == 0) {
            return null;
        }
        return new LinkedHashSet<>(Arrays.asList(annotation.startScopes()));
    }

    private static TestControlScopeObserver resolveScopeObserver(TestContext testContext) {
        Optional<BeanManager> beanManager =
                testContext.getMetadata(SeContainer.class).map(SeContainer::getBeanManager);
        if (beanManager.isEmpty()) {
            return null;
        }
        BeanManager bm = beanManager.get();
        try {
            Bean<?> bean = bm.resolve(bm.getBeans(TestControlScopeObserver.class));
            if (bean == null) {
                return null;
            }
            return (TestControlScopeObserver) bm.getReference(
                    bean,
                    TestControlScopeObserver.class,
                    bm.createCreationalContext(bean));
        } catch (RuntimeException missingBean) {
            return null;
        }
    }
}
