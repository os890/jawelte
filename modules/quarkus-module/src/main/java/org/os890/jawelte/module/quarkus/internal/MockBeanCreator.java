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
package org.os890.jawelte.module.quarkus.internal;

import org.mockito.Mockito;

import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;

/**
 * {@link BeanCreator} that returns a Mockito mock for the
 * implementation class supplied at registration time. ArC instantiates
 * this creator and passes the bean's configurator parameters via
 * {@link SyntheticCreationalContext#getParams()}.
 *
 * <p>Two parameter shapes are supported:
 * <ul>
 *   <li>{@code "implementationClass"} — a {@code Class<?>} resolved
 *       directly by ArC (used for types present in the Jandex index)</li>
 *   <li>{@code "implementationClassName"} — a {@code String} FQCN
 *       loaded reflectively at runtime (used for JDK types that don't
 *       appear in the Jandex index)</li>
 * </ul>
 */
public class MockBeanCreator implements BeanCreator<Object> {

    /**
     * No-arg constructor required by ArC's reflective instantiation
     * of synthetic-bean creators.
     */
    public MockBeanCreator() {
    }

    @Override
    public Object create(SyntheticCreationalContext<Object> context) {
        Class<?> implementationClass = resolveClass(context);
        try {
            return Mockito.mock(implementationClass);
        } catch (RuntimeException unmockable) {
            // Mockito can't subclass certain final / sealed JDK types.
            // Return null — consistent with Mockito's default answer
            // for reference types.
            return null;
        }
    }

    private static Class<?> resolveClass(SyntheticCreationalContext<Object> context) {
        Object implClass = context.getParams().get("implementationClass");
        if (implClass instanceof Class<?> cls) {
            return cls;
        }
        String className = (String) context.getParams().get("implementationClassName");
        if (className != null) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Cannot load mock target class: " + className, e);
            }
        }
        throw new IllegalStateException(
                "Synthetic mock bean is missing both 'implementationClass' and "
                        + "'implementationClassName' params");
    }
}
