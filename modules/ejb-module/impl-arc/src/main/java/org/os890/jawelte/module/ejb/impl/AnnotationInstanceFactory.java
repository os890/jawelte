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
package org.os890.jawelte.module.ejb.impl;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Constructs a default-attribute annotation instance for a
 * {@code Class<? extends Annotation>} resolved at runtime — the
 * standard JDK {@link Proxy#newProxyInstance technique} when the
 * annotation type is not on the compile classpath.
 *
 * <p>ejb-module/impl needs this because scope-module's
 * {@code @TestClassScoped} is not a compile-time dependency: the
 * scope class travels through the typed metadata record on
 * {@code TestContext} and the default mapper instantiates it on
 * first use. Subclassing
 * {@code AnnotationLiteral<TestClassScoped>} does not work — the
 * generic type parameter is erased; the literal's
 * {@code annotationType()} reads the parameter via reflection and
 * returns the wrong class.
 *
 * <p>The handler implements the four mandatory {@link Annotation}
 * methods ({@code annotationType}, {@code equals}, {@code hashCode},
 * {@code toString}) plus the standard
 * {@code default}-attribute resolution for every other invocation —
 * Java's annotation contract guarantees every annotation member has
 * a default when used purely as a marker, which is the case for
 * scope annotations.
 */
abstract class AnnotationInstanceFactory {

    private AnnotationInstanceFactory() {
    }

    /**
     * Create a default-attribute instance of {@code annotationType}.
     *
     * @param annotationType the annotation class to instantiate;
     *                       must not be {@code null}
     * @param <A>            the annotation type
     * @return a {@link Proxy}-backed instance that reports
     *         {@code annotationType()} as {@code annotationType}
     *         and returns each member's default for every other
     *         attribute access
     */
    static <A extends Annotation> A create(Class<A> annotationType) {
        Object proxy = Proxy.newProxyInstance(
                annotationType.getClassLoader(),
                new Class<?>[]{annotationType},
                (instance, method, args) -> dispatch(annotationType, instance, method));
        return annotationType.cast(proxy);
    }

    private static Object dispatch(Class<? extends Annotation> annotationType, Object instance, Method method) {
        switch (method.getName()) {
            case "annotationType":
                return annotationType;
            case "hashCode":
                return System.identityHashCode(instance);
            case "equals":
                // Two synthetic annotation instances are equal iff they
                // are the same instance — annotation equality across
                // proxy instances would require walking every attribute,
                // which we do not need for the bean-discovery use case.
                return Boolean.FALSE;
            case "toString":
                return "@" + annotationType.getName() + "()";
            default:
                return method.getDefaultValue();
        }
    }
}
