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

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;

/**
 * Runtime side of
 * {@link JaweltAutoMockBuildCompatibleExtension#registerSynthetics}'s
 * class-level {@code @TestBean(bean = X.class)} path. Instantiates the
 * target class via its public no-arg constructor for every synthetic
 * bean invocation, mirroring CDI's contract for {@code @Dependent}
 * beans and ArC's behaviour for class-bean alternatives.
 *
 * <p>Reads the target class's FQN from the {@code "targetClass"}
 * parameter and resolves it via the thread context classloader, which
 * under {@code @QuarkusTest} is the deployment classloader that holds
 * the test classpath.
 */
public class TestBeanInstanceSyntheticBeanCreator implements SyntheticBeanCreator<Object> {

    /** Public no-arg constructor required by CDI's reflective creator lookup. */
    public TestBeanInstanceSyntheticBeanCreator() {
    }

    @Override
    public Object create(Instance<Object> lookup, Parameters params) {
        Class<?> targetClass = params.get("targetClass", Class.class);
        if (targetClass == null) {
            throw new IllegalStateException(
                    "TestBeanInstanceSyntheticBeanCreator invoked without a 'targetClass' parameter");
        }
        try {
            return targetClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new IllegalStateException(
                    "Failed to instantiate @TestBean(bean=" + targetClass.getName() + ") via public no-arg constructor",
                    reflectionFailure);
        }
    }
}
