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
package org.os890.jawelte.module.quarkus.runtime;

import org.mockito.Mockito;

import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;

/**
 * Runtime {@link BeanCreator} used by every auto-mock
 * {@code SyntheticBeanBuildItem} that quarkus-module/deployment's
 * {@code JaweltesQuarkusProcessor} registers at build time. The
 * build-time step passes the target interface's FQN as the
 * {@code typeName} parameter; this creator loads the class
 * reflectively and returns a fresh Mockito mock for each bean
 * activation.
 *
 * <p>Mirrors {@code cdi-module/impl}'s {@code MockitoMockFactory}
 * default. A custom {@code MockFactory} contributed by the user is
 * out of scope for the first iteration — Mockito is the only
 * supported runtime mock library under {@code -Pquarkus}; consumers
 * needing a different factory will get it via a follow-up build-time
 * configuration knob.
 */
public class MockBeanCreator implements BeanCreator<Object> {

    /** Build-time parameter key carrying the target interface's FQN. */
    public static final String TYPE_NAME_PARAM = "typeName";

    /** Default constructor used by ArC at synthetic-bean creation time. */
    public MockBeanCreator() {
    }

    @Override
    public Object create(SyntheticCreationalContext<Object> context) {
        Object typeNameParam = context.getParams().get(TYPE_NAME_PARAM);
        if (typeNameParam == null) {
            throw new IllegalStateException(
                    "MockBeanCreator invoked without a typeName parameter; "
                            + "the build-time step must set it on the SyntheticBeanBuildItem.");
        }
        String typeName = typeNameParam.toString();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = MockBeanCreator.class.getClassLoader();
        }
        try {
            Class<?> type = Class.forName(typeName, false, classLoader);
            return Mockito.mock(type);
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException(
                    "MockBeanCreator could not load target type " + typeName, missing);
        }
    }
}
