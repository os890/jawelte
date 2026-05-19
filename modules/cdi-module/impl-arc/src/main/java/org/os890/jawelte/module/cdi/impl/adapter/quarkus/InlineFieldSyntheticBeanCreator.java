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
package org.os890.jawelte.module.cdi.impl.adapter.quarkus;

import java.lang.reflect.Field;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;

/**
 * Runtime side of
 * {@link JaweltAutoMockBuildCompatibleExtension#registerSynthetics}'s
 * inline-field path. Each invocation reads the named static field
 * on the named declaring class via reflection and returns its
 * current value.
 *
 * <p>The {@code @TestBean} static field is required (by jawelte's
 * pre-bootstrap validation in
 * {@code CdiTestBeanContainer.collectInlineFields}) to be both
 * {@code static} and non-{@code null} — if either invariant breaks,
 * the JUnit extension throws before Quarkus boots and the build
 * never reaches this creator.
 */
public class InlineFieldSyntheticBeanCreator implements SyntheticBeanCreator<Object> {

    /** Public no-arg constructor required by CDI's reflective creator lookup. */
    public InlineFieldSyntheticBeanCreator() {
    }

    @Override
    public Object create(Instance<Object> lookup, Parameters params) {
        String declaringClassName = params.get("declaringClass", String.class);
        String fieldName = params.get("fieldName", String.class);
        if (declaringClassName == null || fieldName == null) {
            throw new IllegalStateException(
                    "InlineFieldSyntheticBeanCreator invoked without 'declaringClass' / 'fieldName'");
        }
        try {
            Class<?> declaringClass = Class.forName(declaringClassName, true,
                    Thread.currentThread().getContextClassLoader());
            Field field = declaringClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(null);
            if (value == null) {
                throw new IllegalStateException(
                        "@TestBean static field " + declaringClassName + "." + fieldName + " is null");
            }
            return value;
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new IllegalStateException(
                    "Failed to read @TestBean field "
                            + declaringClassName + "." + fieldName,
                    reflectionFailure);
        }
    }
}
