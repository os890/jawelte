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
package org.os890.jawelte.module.jaxrs.impl.adapter.extension;

import java.io.Serial;
import java.lang.annotation.Annotation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.util.AnnotationLiteral;

import org.os890.jawelte.module.jaxrs.impl.TestUrlHolder;
import org.os890.jawelte.module.scope.api.TestClassScoped;
import org.os890.jawelte.module.scope.api.TestMethodScoped;

/**
 * Single CDI {@link Extension} shipped by jaxrs-module/impl that
 * carries two {@link ProcessAnnotatedType} remap responsibilities:
 *
 * <ol>
 *   <li><b>Unconditional global remap of {@link SessionScoped}.</b>
 *       Every class annotated {@code @SessionScoped} (anywhere in
 *       the bean archive) has its {@code @SessionScoped} replaced
 *       by {@link TestMethodScoped}. Web-tier session semantics
 *       don't fit the test-method scope of a per-test-class
 *       container; the remap makes {@code @SessionScoped} beans
 *       behave as one-instance-per-test-method, which is the
 *       useful test analogue.</li>
 *   <li><b>Conditional scope upgrade of {@link TestUrlHolder}.</b>
 *       When testcontrol-module is on the classpath (probed at
 *       extension load via
 *       {@code Class.forName("org.os890.jawelte.module.testcontrol.api.TestControl")}),
 *       {@link TestUrlHolder}'s declared {@link ApplicationScoped}
 *       is replaced with {@link TestClassScoped} so the URL holder
 *       and the embedded server share the same per-test-class
 *       lifetime exactly. Without testcontrol-module on the
 *       classpath, {@code @TestClassScoped} would still be
 *       available (jaxrs-module/impl compile-depends on
 *       scope-module/api), but the upgrade is gated on the
 *       <em>presence of testcontrol-module</em> rather than on
 *       scope-module because under cdi-module's per-test-class CDI
 *       container the two scopes are observably equivalent — the
 *       upgrade is a meaningful semantic guarantee only when a
 *       consumer specifically ships testcontrol-module (and hence
 *       wants the explicit per-test-class scope tag everywhere).</li>
 * </ol>
 *
 * <p><b>Independence from {@code @TestControl}.</b> Neither remap
 * requires any test method to carry {@code @TestControl} (or any
 * other testcontrol-module annotation). The {@code @SessionScoped}
 * remap fires on every CDI bootstrap. The {@code TestUrlHolder}
 * upgrade fires every time the CDI runtime delivers
 * {@code ProcessAnnotatedType<TestUrlHolder>}, provided
 * testcontrol-module is on the classpath.
 *
 * <p><b>Stateless.</b> No instance fields. Discovered via
 * {@code META-INF/services/jakarta.enterprise.inject.spi.Extension};
 * instantiated once per {@code SeContainer} startup.
 */
public class JaxRsCdiExtension implements Extension {

    private static final boolean TESTCONTROL_PRESENT = isTestControlOnClasspath();

    private static final String TESTCONTROL_API_PROBE_CLASS =
            "org.os890.jawelte.module.testcontrol.api.TestControl";

    /** No-arg constructor required by the CDI runtime. */
    public JaxRsCdiExtension() {
    }

    void onProcessAnnotatedType(@Observes ProcessAnnotatedType<?> event) {
        AnnotatedType<?> target = event.getAnnotatedType();
        Class<?> javaClass = target.getJavaClass();

        if (TESTCONTROL_PRESENT && javaClass.equals(TestUrlHolder.class)) {
            event.configureAnnotatedType()
                    .remove(annotation -> annotation.annotationType().equals(ApplicationScoped.class))
                    .add(TestClassScopedLiteral.INSTANCE);
            return;
        }

        if (target.isAnnotationPresent(SessionScoped.class)) {
            event.configureAnnotatedType()
                    .remove(annotation -> annotation.annotationType().equals(SessionScoped.class))
                    .add(TestMethodScopedLiteral.INSTANCE);
        }
    }

    private static boolean isTestControlOnClasspath() {
        try {
            Class.forName(
                    TESTCONTROL_API_PROBE_CLASS,
                    false,
                    JaxRsCdiExtension.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    /**
     * Singleton {@link TestMethodScoped} annotation literal used by
     * the {@code @SessionScoped} remap. {@code annotationType()} is
     * overridden explicitly so the literal stays correct regardless
     * of how the parameterized superclass's generic type erasure is
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

    /**
     * Singleton {@link TestClassScoped} annotation literal used by
     * the {@code TestUrlHolder} scope upgrade. Same shape as
     * {@link TestMethodScopedLiteral}.
     */
    private static class TestClassScopedLiteral
            extends AnnotationLiteral<TestClassScoped>
            implements TestClassScoped {

        @Serial
        private static final long serialVersionUID = 1L;

        static final TestClassScopedLiteral INSTANCE = new TestClassScopedLiteral();

        @Override
        public Class<? extends Annotation> annotationType() {
            return TestClassScoped.class;
        }
    }
}
