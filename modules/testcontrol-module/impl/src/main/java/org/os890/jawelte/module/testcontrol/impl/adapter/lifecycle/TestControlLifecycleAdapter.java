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
import jakarta.enterprise.inject.spi.CDI;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;
import org.os890.jawelte.module.testcontrol.api.TestControl;
import org.os890.jawelte.module.testcontrol.impl.adapter.data.TestDataHandler;
import org.os890.jawelte.module.testcontrol.impl.adapter.observer.TestControlScopeObserver;

/**
 * {@link TestModuleLifecyclePort} adapter shipped by
 * testcontrol-module/impl. Orchestrates three things per test method:
 *
 * <ol>
 *   <li>Resolve the active test method's {@link TestControl} and
 *       publish it on {@link TestContext} under the
 *       {@code TestControl.class} key so the
 *       {@link TestDataHandler}'s {@code AfterTestTransaction}
 *       observer can read it.</li>
 *   <li>Push the {@code startScopes} allow-list to the
 *       {@link TestControlScopeObserver} CDI bean before
 *       scope-module's adapter fires its {@code BeforeScopeStarted}
 *       events.</li>
 *   <li>Drive the test-data pipeline through {@link TestDataHandler}:
 *       phases 1–3 ({@code dbIn/} + {@code dbUpdate/} inside a
 *       {@code @Transactional} template, commit on lambda return)
 *       in {@code beforeEach}; the {@code dbExpected/} fallback in
 *       {@code afterEach} — only when
 *       {@link TestDataHandler#didAlreadyVerify()} is still
 *       {@code false} (otherwise the transactional path already
 *       verified through
 *       {@link TestDataHandler#onAfterTestTransaction}). The
 *       {@code afterEach} method always calls
 *       {@link TestDataHandler#clearActive()} in a {@code finally}
 *       block — independent of whether {@link TestControl} was
 *       bound, whether the {@code testData} array was empty, and
 *       whether the verify step threw — so the next test method
 *       starts with a clean handler state. The handler is
 *       {@code @ApplicationScoped}, so a missed reset would leak
 *       state across every later method in the same container.</li>
 * </ol>
 *
 * <p><b>Priority.</b> {@code @Priority(50)} — lowest among jawelte's
 * lifecycle adapters, so {@code beforeAll} / {@code beforeEach} run
 * <em>first</em> in the chain (before scope-module at 100 fires
 * {@code BeforeScopeStarted}, and before jpa-module at 200 opens a
 * transaction). {@code afterAll} / {@code afterEach} run
 * <em>last</em> (LIFO) so the non-transactional {@code dbExpected/}
 * fallback sees the post-test database state — for non-transactional
 * tests the JPA module skips its table cleanup, so the data is still
 * present when the assertion runs.
 *
 * <p><b>Annotation lookup.</b> Uses JUnit Jupiter's
 * {@link AnnotationSupport#findAnnotation(java.lang.reflect.AnnotatedElement,
 * Class) AnnotationSupport.findAnnotation} on the active test method
 * (read off the per-method {@link ExtensionContext} bound on
 * {@link TestContext} by core/impl's {@code DelegatingJUnitExtension}).
 * The Jupiter platform walks the test class hierarchy when the
 * method is not overridden — the inheritance contract documented on
 * {@link TestControl}.
 *
 * <p><b>State.</b> Stateless — no instance fields. Per-method state
 * lives on {@link TestContext} (the {@code TestControl} metadata
 * key, unbound in {@code afterEach}) and on the observer / handler
 * beans' own volatile state (the handler's {@code activeAnnotation}
 * and {@code verifiedThisMethod} flags, cleared by
 * {@link TestDataHandler#clearActive()}).
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
        configureScopeObserver(annotation.orElse(null));
        if (annotation.isPresent() && annotation.get().testData().length > 0) {
            TestDataHandler handler = resolveBean(TestDataHandler.class);
            if (handler != null) {
                handler.seedAll(annotation.get());
            }
        }
    }

    @Override
    public void afterEach(TestContext testContext) {
        // Handler is @ApplicationScoped: resolve once so clearActive runs in finally regardless of how verifyAll exits.
        TestDataHandler handler = resolveBean(TestDataHandler.class);
        try {
            Optional<TestControl> annotation = testContext.getMetadata(TestControl.class);
            if (handler != null
                    && annotation.isPresent()
                    && annotation.get().testData().length > 0
                    && !handler.didAlreadyVerify()) {
                handler.verifyAll();
            }
        } finally {
            try {
                if (handler != null) {
                    handler.clearActive();
                }
            } finally {
                // Reset the scope-filter allow-list so the next test method
                // starts with a fresh context. TestControlScopeObserver is
                // @ApplicationScoped (container lifetime), so without this its
                // allow-list would leak into the next method — and because
                // containerPort.beforeEach fires BeforeScopeStarted(RequestScoped)
                // BEFORE this adapter reconfigures the list, a stale restrictive
                // allow-list could wrongly veto the next method's request scope.
                configureScopeObserver(null);
                testContext.unbindMetadata(TestControl.class);
            }
        }
    }

    private static void configureScopeObserver(TestControl annotation) {
        TestControlScopeObserver observer = resolveBean(TestControlScopeObserver.class);
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

    private static <T> T resolveBean(Class<T> beanType) {
        try {
            return CDI.current().select(beanType).get();
        } catch (RuntimeException noContainerOrNoBean) {
            return null;
        }
    }
}
