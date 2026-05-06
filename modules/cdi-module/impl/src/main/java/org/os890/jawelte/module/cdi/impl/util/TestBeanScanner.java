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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.os890.jawelte.core.api.TestBean;
import org.os890.jawelte.core.api.TestBeans;

/**
 * Scans a test class for {@link TestBean} declarations. Collects:
 * <ul>
 *   <li>class-level and meta-annotation {@code @TestBean} entries
 *       (recursive through annotation hierarchies; cycle-safe);</li>
 *   <li>static-field {@code @TestBean} entries on the test class and
 *       its superclasses.</li>
 * </ul>
 *
 * <p>Class-level discoveries are deduplicated by target class so the
 * same {@code @TestBean(bean=X)} reachable through multiple
 * meta-annotation paths is recorded once. Static-field entries are
 * deduplicated by the declaring field (class + name).
 *
 * <p>Skips meta-annotation traversal into the {@code java.} and
 * {@code jakarta.} annotation-type packages — those are not where
 * users place {@code @TestBean}.
 */
public abstract class TestBeanScanner {

    /**
     * Suppressed-instantiation constructor. The class is
     * {@code abstract} so direct {@code new} is impossible; the
     * explicit declaration silences {@code javadoc -doclint:all} on
     * the otherwise synthesized default constructor.
     */
    protected TestBeanScanner() {
    }

    /**
     * Scan the given test class. Walks the class + supertypes and the
     * meta-annotation hierarchy; returns the discovered targets in a
     * single immutable {@link Result}.
     *
     * @param testClass the test class to scan
     * @return the scan result; never {@code null}
     */
    public static Result scan(Class<?> testClass) {
        Map<Class<?>, TestBean> beanTargets = new LinkedHashMap<>();
        Map<Class<?>, TestBean> producerTargets = new LinkedHashMap<>();
        List<StaticField> staticFields = new ArrayList<>();
        Set<Class<? extends Annotation>> visitedAnnotations = new HashSet<>();

        Class<?> current = testClass;
        while (current != null && current != Object.class) {
            collectAnnotationDeclarations(current, beanTargets, producerTargets, visitedAnnotations);
            collectStaticFields(current, staticFields);
            current = current.getSuperclass();
        }

        return new Result(
                List.copyOf(beanTargets.values()),
                List.copyOf(producerTargets.values()),
                List.copyOf(beanTargets.keySet()),
                List.copyOf(producerTargets.keySet()),
                Collections.unmodifiableList(staticFields));
    }

    private static void collectAnnotationDeclarations(
            Class<?> source,
            Map<Class<?>, TestBean> beanTargets,
            Map<Class<?>, TestBean> producerTargets,
            Set<Class<? extends Annotation>> visitedAnnotations) {
        for (Annotation annotation : source.getDeclaredAnnotations()) {
            recordTestBeanDeclarations(annotation, beanTargets, producerTargets, visitedAnnotations);
        }
    }

    private static void recordTestBeanDeclarations(
            Annotation annotation,
            Map<Class<?>, TestBean> beanTargets,
            Map<Class<?>, TestBean> producerTargets,
            Set<Class<? extends Annotation>> visitedAnnotations) {
        if (annotation instanceof TestBean tb) {
            recordTestBean(tb, beanTargets, producerTargets);
            return;
        }
        if (annotation instanceof TestBeans container) {
            for (TestBean tb : container.value()) {
                recordTestBean(tb, beanTargets, producerTargets);
            }
            return;
        }
        Class<? extends Annotation> annotationType = annotation.annotationType();
        if (!visitedAnnotations.add(annotationType)) {
            return;
        }
        String packageName = annotationType.getPackageName();
        if (packageName.startsWith("java.") || packageName.startsWith("jakarta.")) {
            return;
        }
        for (Annotation meta : annotationType.getDeclaredAnnotations()) {
            recordTestBeanDeclarations(meta, beanTargets, producerTargets, visitedAnnotations);
        }
    }

    private static void recordTestBean(
            TestBean tb,
            Map<Class<?>, TestBean> beanTargets,
            Map<Class<?>, TestBean> producerTargets) {
        boolean hasBean = tb.bean() != void.class;
        boolean hasProducer = tb.beanProducer() != void.class;
        if (hasBean && hasProducer) {
            throw new IllegalStateException(
                    "@TestBean must specify either bean or beanProducer, not both. Found on: "
                            + tb.bean().getName() + " / " + tb.beanProducer().getName());
        }
        if (hasBean) {
            beanTargets.putIfAbsent(tb.bean(), tb);
        }
        if (hasProducer) {
            producerTargets.putIfAbsent(tb.beanProducer(), tb);
        }
    }

    private static void collectStaticFields(Class<?> declaringClass, List<StaticField> out) {
        for (Field field : declaringClass.getDeclaredFields()) {
            TestBean testBean = field.getAnnotation(TestBean.class);
            if (testBean == null) {
                continue;
            }
            if (testBean.bean() != void.class) {
                throw new IllegalStateException(
                        "@TestBean field " + declaringClass.getName() + "." + field.getName()
                                + " has both a field value and bean attribute. Use one or the other.");
            }
            if (!Modifier.isStatic(field.getModifiers())) {
                throw new IllegalStateException(
                        "@TestBean field must be static: "
                                + declaringClass.getName() + "." + field.getName());
            }
            field.setAccessible(true);
            Object value;
            try {
                value = field.get(null);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(
                        "Could not read @TestBean static field "
                                + declaringClass.getName() + "." + field.getName(), e);
            }
            if (value == null) {
                throw new IllegalStateException(
                        "@TestBean field is null: "
                                + declaringClass.getName() + "." + field.getName());
            }
            out.add(new StaticField(declaringClass, field, value));
        }
    }

    /**
     * Immutable record of a {@code @TestBean}-declared static field.
     *
     * @param declaringClass the class that declares the field (the
     *                       test class or one of its superclasses)
     * @param field          the reflective field handle
     * @param value          the field's current value (already
     *                       null-checked by the scanner)
     */
    public record StaticField(Class<?> declaringClass, Field field, Object value) {
    }

    /**
     * Immutable scan result. The {@code beanTypes} / {@code producerTypes}
     * lists are the deduplicated target classes; the {@code beanAnnotations}
     * / {@code producerAnnotations} lists carry the original
     * {@link TestBean} annotation metadata in matching order.
     *
     * @param beanAnnotations    the {@code @TestBean} annotations whose
     *                           {@code bean} attribute is populated
     * @param producerAnnotations the {@code @TestBean} annotations whose
     *                           {@code beanProducer} attribute is populated
     * @param beanTypes          the deduplicated target classes for
     *                           {@code @TestBean(bean=...)}
     * @param producerTypes      the deduplicated target classes for
     *                           {@code @TestBean(beanProducer=...)}
     * @param staticFields       the {@code @TestBean}-annotated static fields
     */
    public record Result(
            List<TestBean> beanAnnotations,
            List<TestBean> producerAnnotations,
            List<Class<?>> beanTypes,
            List<Class<?>> producerTypes,
            List<StaticField> staticFields) {

        /**
         * Whether the given type is a {@code @TestBean} target on the
         * scanned test class — either as {@code bean=}, as
         * {@code beanProducer=}, or as the declared type of a static
         * field.
         *
         * @param rawType the candidate type
         * @return {@code true} if {@code rawType} is targeted
         */
        public boolean isTarget(Class<?> rawType) {
            if (beanTypes.contains(rawType) || producerTypes.contains(rawType)) {
                return true;
            }
            for (StaticField staticField : staticFields) {
                if (staticField.field().getType().equals(rawType)) {
                    return true;
                }
            }
            return false;
        }
    }
}
