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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Scope;

import org.os890.jawelte.core.api.ConfigBean;
import org.os890.jawelte.module.scope.api.TestClassScoped;

/**
 * CDI Extension shipped by scope-module/impl. Single responsibility:
 * at {@code ProcessAnnotatedType} time, every class carrying the
 * {@link ConfigBean} stereotype has its effective scope remapped from
 * {@link ApplicationScoped} (the scope contributed by the
 * {@code @ConfigBean} stereotype) to {@link TestClassScoped}, so that
 * user configuration beans are recreated per test class instead of
 * once per CDI container.
 *
 * <p><b>Trigger surface.</b> The remap fires unconditionally when
 * scope-module is on the classpath — it does <em>not</em> require any
 * test method to carry {@code @TestControl} (or any other annotation
 * shipped by testcontrol-module). There is no opt-out for individual
 * {@code @ConfigBean} classes.
 *
 * <p><b>Scope precedence.</b> Only the implicit
 * {@code @ApplicationScoped} contribution from the
 * {@code @ConfigBean} stereotype is remapped. If a user has declared
 * a different scope on the class directly (e.g.
 * {@code @ConfigBean @RequestScoped}), the explicit scope wins and
 * this extension leaves the class alone. The detection rule:
 *
 * <ul>
 *   <li>iterate the class's declared annotations,</li>
 *   <li>skip {@code @ConfigBean} (its stereotype-contributed scope is
 *       what we are about to override) and any literal
 *       {@code @ApplicationScoped} (we are about to replace it),</li>
 *   <li>if any remaining annotation is meta-annotated with
 *       {@link NormalScope} or {@link Scope}, the class has an
 *       explicit non-{@code @ApplicationScoped} scope and the
 *       extension skips it.</li>
 * </ul>
 *
 * <p><b>Mechanism.</b> The remap uses
 * {@code ProcessAnnotatedType#configureAnnotatedType()}: any literal
 * {@link ApplicationScoped} on the class is removed (covers the case
 * where a user wrote {@code @ConfigBean @ApplicationScoped}
 * redundantly), and a {@link TestClassScoped} literal is added.
 * Class-level explicit scope wins over stereotype-contributed scope
 * in the CDI bean discovery rules, so adding {@code @TestClassScoped}
 * to the class is sufficient to override the
 * {@code @ConfigBean → @ApplicationScoped} chain even when nothing is
 * removed.
 *
 * <p><b>Lifecycle.</b> Stateless — no instance fields. Discovered
 * via the {@code META-INF/services/jakarta.enterprise.inject.spi.Extension}
 * registration shipped in this module alongside
 * {@link TestScopeCdiExtension}. Instantiated once per
 * {@code SeContainer} startup.
 */
public class ConfigBeanScopeRemapCdiExtension implements Extension {

    /** No-arg constructor required by the CDI runtime. */
    public ConfigBeanScopeRemapCdiExtension() {
    }

    void onProcessAnnotatedType(@Observes ProcessAnnotatedType<?> event) {
        AnnotatedType<?> target = event.getAnnotatedType();
        if (!target.isAnnotationPresent(ConfigBean.class)) {
            return;
        }
        if (hasExplicitNonApplicationScopeDeclaration(target)) {
            return;
        }
        event.configureAnnotatedType()
                .remove(annotation -> annotation.annotationType().equals(ApplicationScoped.class))
                .add(TestClassScopedLiteral.INSTANCE);
    }

    private static boolean hasExplicitNonApplicationScopeDeclaration(AnnotatedType<?> target) {
        for (Annotation declared : target.getAnnotations()) {
            Class<? extends Annotation> annotationType = declared.annotationType();
            if (annotationType.equals(ConfigBean.class) || annotationType.equals(ApplicationScoped.class)) {
                continue;
            }
            if (annotationType.isAnnotationPresent(NormalScope.class)
                    || annotationType.isAnnotationPresent(Scope.class)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Singleton {@link TestClassScoped} annotation literal used by
     * the remap. {@code annotationType()} is overridden explicitly so
     * the literal stays correct regardless of how the parameterized
     * superclass's generic type erasure is resolved at runtime.
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
