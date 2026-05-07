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

import jakarta.annotation.Priority;

import org.mockito.Mockito;
import org.os890.jawelte.module.cdi.api.port.MockFactory;

/**
 * Default {@link MockFactory}. Wraps {@code Mockito.mock(Class)} and
 * returns {@code null} when Mockito throws (typical for unmockable
 * bootstrap JDK classes such as {@code Class}, {@code String}, the
 * primitive wrappers, etc.); the cdi-module's CDI Extension then
 * leaves the injection point unsatisfied so CDI's own deployment
 * validation surfaces the offending type.
 *
 * <p>Annotated {@code @Priority(Integer.MAX_VALUE)} so any
 * user-supplied {@link MockFactory} with a lower priority value
 * automatically wins via the project-wide
 * {@code ServicePriorityResolver}.
 */
@Priority(Integer.MAX_VALUE)
public class MockitoMockFactory implements MockFactory {

    /** No-arg constructor required by {@code ServiceLoader}. */
    public MockitoMockFactory() {
    }

    @Override
    public <T> T create(Class<T> rawType) {
        try {
            return Mockito.mock(rawType);
        } catch (RuntimeException unmockable) {
            return null;
        }
    }
}
