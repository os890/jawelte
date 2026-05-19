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

import java.lang.reflect.Method;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;

/**
 * Runtime side of
 * {@link JaweltAutoMockBuildCompatibleExtension#registerSynthetics}'s
 * class-level {@code @TestBean(beanProducer = Y.class)} path.
 * Instantiates {@code Y} via its public no-arg constructor and invokes
 * the named {@code @Produces} method on it, returning its result.
 *
 * <p>Reads two parameters: {@code "producerClass"} (the FQN of the
 * declaring class) and {@code "methodName"}. Only parameterless
 * {@code @Produces} methods are supported in this first pass — the
 * standalone-ArC path additionally supports {@code @Produces} method
 * injection points; that needs separate plumbing under @QuarkusTest
 * because synthetic beans cannot declare injection points.
 */
public class TestBeanProducerMethodSyntheticBeanCreator implements SyntheticBeanCreator<Object> {

    /** Public no-arg constructor required by CDI's reflective creator lookup. */
    public TestBeanProducerMethodSyntheticBeanCreator() {
    }

    @Override
    public Object create(Instance<Object> lookup, Parameters params) {
        String producerClassName = params.get("producerClass", String.class);
        String methodName = params.get("methodName", String.class);
        if (producerClassName == null || methodName == null) {
            throw new IllegalStateException(
                    "TestBeanProducerMethodSyntheticBeanCreator invoked without 'producerClass' / 'methodName'");
        }
        try {
            Class<?> producerClass = Class.forName(producerClassName, true,
                    Thread.currentThread().getContextClassLoader());
            Object producer = producerClass.getDeclaredConstructor().newInstance();
            Method method = producerClass.getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(producer);
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new IllegalStateException(
                    "Failed to invoke @TestBean(beanProducer=" + producerClassName + ")#" + methodName,
                    reflectionFailure);
        }
    }
}
