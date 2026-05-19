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

import org.mockito.Mockito;

/**
 * Runtime side of
 * {@link JaweltAutoMockBuildCompatibleExtension#registerSynthetics}'s
 * auto-mock path. Each invocation of {@link #create} produces a
 * fresh Mockito mock of the type stored in the synthetic bean's
 * {@code "targetType"} parameter.
 */
public class MockSyntheticBeanCreator implements SyntheticBeanCreator<Object> {

    /** Public no-arg constructor required by CDI's reflective creator lookup. */
    public MockSyntheticBeanCreator() {
    }

    @Override
    public Object create(Instance<Object> lookup, Parameters params) {
        Class<?> targetType = params.get("targetType", Class.class);
        if (targetType == null) {
            throw new IllegalStateException(
                    "MockSyntheticBeanCreator invoked without a 'targetType' parameter");
        }
        try {
            return Mockito.mock(targetType);
        } catch (RuntimeException unmockable) {
            // Mockito refuses some final / sealed JDK types — match
            // the standalone-ArC MockBeanCreator's behaviour and
            // surface null, which CDI's @Dependent contract permits.
            return null;
        }
    }
}
