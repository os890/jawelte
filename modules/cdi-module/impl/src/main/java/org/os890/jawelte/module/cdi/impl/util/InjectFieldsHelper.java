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
package org.os890.jawelte.module.cdi.impl.util;

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionTarget;
import jakarta.enterprise.inject.spi.InjectionTargetFactory;

/**
 * Populates {@code @Inject} fields on the JUnit-provided test
 * instance via CDI's manual-injection support for unmanaged
 * instances. The test class itself is never registered as a CDI
 * bean — JUnit owns the instance, and there is no JUnit hook that
 * lets the framework return a CDI-managed replacement — so the
 * standard manual-injection sequence
 * ({@code createAnnotatedType} → {@code getInjectionTargetFactory}
 * → {@code createInjectionTarget(null)} → {@code inject}) is the
 * single path used to populate the test instance's
 * {@code @Inject} / {@code @Inject Provider<X>} /
 * {@code @Inject Instance<X>} fields, including inherited ones.
 *
 * <p>cdi-module performs no per-field reflective walk of its own —
 * the underlying CDI runtime handles inherited fields, qualifiers,
 * generic types, and {@code Provider}/{@code Instance} wrappers.
 */
public abstract class InjectFieldsHelper {

    /**
     * Suppressed-instantiation constructor. The class is
     * {@code abstract} so direct {@code new} is impossible; the
     * explicit declaration silences {@code javadoc -doclint:all} on
     * the otherwise synthesized default constructor.
     */
    protected InjectFieldsHelper() {
    }

    /**
     * Inject {@code @Inject} fields on the given test instance via
     * the CDI runtime's manual-injection support.
     *
     * @param beanManager  the active {@code BeanManager}
     * @param testInstance the JUnit-created test instance
     */
    public static void inject(BeanManager beanManager, Object testInstance) {
        @SuppressWarnings("unchecked")
        AnnotatedType<Object> annotatedType =
                (AnnotatedType<Object>) beanManager.createAnnotatedType(testInstance.getClass());
        InjectionTargetFactory<Object> factory = beanManager.getInjectionTargetFactory(annotatedType);
        InjectionTarget<Object> injectionTarget = factory.createInjectionTarget(null);
        CreationalContext<Object> creationalContext = beanManager.createCreationalContext(null);
        injectionTarget.inject(testInstance, creationalContext);
    }
}
