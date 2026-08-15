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
package org.os890.jawelte.module.resource.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Resource;
import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.AnnotatedType;

/**
 * Finds the {@code @Resource} fields of a type and works out the name
 * each one asks for.
 *
 * <p><b>Only named declarations are taken.</b> A bare {@code @Resource}
 * with no {@code lookup}, {@code mappedName} or {@code name} is left
 * alone: in Jakarta EE its name is inferred from the declaring class
 * and field ({@code java:comp/env/<class>/<field>}), which is a much
 * larger surface than this module covers today. Leaving it untouched
 * keeps the behaviour of such a field exactly what it was before the
 * module was on the classpath, rather than failing a deployment over a
 * declaration nobody asked this module to handle.
 */
public abstract class ResourceFields {

    /** Suppress instantiation; the class is a static-method holder. */
    protected ResourceFields() {
    }

    /**
     * One {@code @Resource} field and the name it resolves under.
     *
     * @param field the declared field, made accessible by the caller
     * @param name  the name to resolve, already trimmed and non-blank
     */
    public record Target(Field field, String name) {
    }

    /**
     * Collect the named {@code @Resource} fields of a type.
     *
     * @param annotatedType the type the CDI runtime is processing
     * @return one entry per named {@code @Resource} field, in
     *         declaration order; empty when the type declares none,
     *         which is the signal to leave its
     *         {@code InjectionTarget} alone
     */
    public static List<Target> of(AnnotatedType<?> annotatedType) {
        List<Target> targets = new ArrayList<>();
        for (AnnotatedField<?> annotatedField : annotatedType.getFields()) {
            Resource resource = annotatedField.getAnnotation(Resource.class);
            if (resource == null) {
                continue;
            }
            String name = nameOf(resource);
            if (name == null) {
                continue;
            }
            targets.add(new Target(annotatedField.getJavaMember(), name));
        }
        return targets;
    }

    /**
     * The name a declaration asks for, preferring the member that is
     * least ambiguous: {@code lookup} names a resource directly,
     * {@code mappedName} names a vendor-specific global, and
     * {@code name} is the component-relative one.
     *
     * @param resource the annotation on the field
     * @return the name, or {@code null} for a bare declaration
     */
    private static String nameOf(Resource resource) {
        String lookup = resource.lookup().trim();
        if (!lookup.isEmpty()) {
            return lookup;
        }
        String mappedName = resource.mappedName().trim();
        if (!mappedName.isEmpty()) {
            return mappedName;
        }
        String name = resource.name().trim();
        if (!name.isEmpty()) {
            return name;
        }
        return null;
    }
}
