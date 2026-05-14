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

import java.io.Serial;
import java.lang.annotation.Annotation;

import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.util.AnnotationLiteral;

import org.os890.jawelte.module.scope.api.TestMethodScoped;

/**
 * CDI Extension shipped by scope-module/impl. Single
 * responsibility: at {@code ProcessAnnotatedType} time, every class
 * annotated {@link SessionScoped @SessionScoped} has its scope
 * rewritten to {@link TestMethodScoped @TestMethodScoped}.
 *
 * <p><b>Why.</b> The web-tier session semantics of
 * {@code @SessionScoped} don't fit the per-test-method-or-class
 * CDI container jawelte boots; "session" effectively maps to "the
 * lifetime of one test method" in this setting. The remap turns
 * {@code @SessionScoped} into one-instance-per-test-method state,
 * which is the useful test analogue — and fires regardless of
 * whether the bean is used by a JAX-RS resource or directly
 * injected into a test class.
 *
 * <p><b>Why scope-module.</b> scope-module owns the longer-lived
 * test scopes ({@code @TestMethodScoped}, {@code @TestClassScoped}),
 * so every scope remap that targets one of them lives here as a
 * sibling to {@link ConfigBeanScopeRemapCdiExtension} (which
 * rewrites {@code @ConfigBean}'s stereotype-contributed scope to
 * {@code @TestClassScoped}). Modules that consume the remap (e.g.
 * jaxrs-module's resources that declare beans as
 * {@code @SessionScoped}) do NOT ship their own CDI Extension for
 * this — keeping JUnit/CDI scope-rewriting concerns concentrated
 * in scope-module avoids per-feature accretion of remap types
 * across the module set.
 *
 * <p><b>Mechanism.</b> Uses
 * {@code ProcessAnnotatedType#configureAnnotatedType()} to remove
 * the {@code @SessionScoped} marker from the
 * {@code AnnotatedType} and add a {@code @TestMethodScoped}
 * literal in its place. CDI validation then sees the type as
 * a non-passivating-scoped bean — so the user's
 * {@code @SessionScoped} class doesn't need to implement
 * {@link java.io.Serializable} (the passivating-scope
 * requirement is checked AFTER {@code ProcessAnnotatedType}).
 *
 * <p><b>Lifecycle.</b> Stateless — no instance fields. Discovered
 * via the
 * {@code META-INF/services/jakarta.enterprise.inject.spi.Extension}
 * registration shipped in this module alongside
 * {@link TestScopeCdiExtension} and
 * {@link ConfigBeanScopeRemapCdiExtension}. Instantiated once
 * per {@code SeContainer} startup.
 */
public class SessionScopeRemapCdiExtension implements Extension {

    /** No-arg constructor required by the CDI runtime. */
    public SessionScopeRemapCdiExtension() {
    }

    void onProcessAnnotatedType(@Observes ProcessAnnotatedType<?> event) {
        AnnotatedType<?> target = event.getAnnotatedType();
        if (!target.isAnnotationPresent(SessionScoped.class)) {
            return;
        }
        event.configureAnnotatedType()
                .remove(annotation -> annotation.annotationType().equals(SessionScoped.class))
                .add(TestMethodScopedLiteral.INSTANCE);
    }

    /**
     * Singleton {@link TestMethodScoped} annotation literal used
     * by the remap. {@code annotationType()} is overridden
     * explicitly so the literal stays correct regardless of how
     * the parameterized superclass's generic type erasure is
     * resolved at runtime.
     */
    private static class TestMethodScopedLiteral
            extends AnnotationLiteral<TestMethodScoped>
            implements TestMethodScoped {

        @Serial
        private static final long serialVersionUID = 1L;

        static final TestMethodScopedLiteral INSTANCE = new TestMethodScopedLiteral();

        @Override
        public Class<? extends Annotation> annotationType() {
            return TestMethodScoped.class;
        }
    }
}
