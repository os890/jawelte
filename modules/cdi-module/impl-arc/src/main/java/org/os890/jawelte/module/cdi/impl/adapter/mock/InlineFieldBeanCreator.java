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
package org.os890.jawelte.module.cdi.impl.adapter.mock;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;

/**
 * {@link BeanCreator} that reads a {@code static} field value from a
 * test class. The declaring class FQCN and the field name are passed
 * via the bean's configurator parameters; the field must be static
 * and accessible (the creator calls {@link Field#setAccessible(boolean)}).
 *
 * <p>Used to back {@code @TestBean}-annotated static fields on the
 * test class so the field value becomes the bean instance instead of
 * an auto-mock.
 */
public class InlineFieldBeanCreator implements BeanCreator<Object> {

    /**
     * No-arg constructor required by ArC's reflective instantiation
     * of synthetic-bean creators.
     */
    public InlineFieldBeanCreator() {
    }

    @Override
    public Object create(SyntheticCreationalContext<Object> context) {
        String declaringClass = (String) context.getParams().get("declaringClass");
        String fieldName = (String) context.getParams().get("fieldName");
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = InlineFieldBeanCreator.class.getClassLoader();
            }
            Class<?> clazz = cl.loadClass(declaringClass);
            Field field = clazz.getDeclaredField(fieldName);
            if (!Modifier.isStatic(field.getModifiers())) {
                throw new IllegalStateException(
                        "@TestBean field must be static: " + declaringClass + "." + fieldName);
            }
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to read @TestBean field: " + declaringClass + "." + fieldName, e);
        }
    }
}
