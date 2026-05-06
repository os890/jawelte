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

import org.mockito.Mockito;

/**
 * Thin wrapper around {@code Mockito.mock(Class)}. Returns
 * {@code null} when Mockito throws (typical for unmockable bootstrap
 * JDK classes such as {@code Class}, {@code String}, primitive
 * wrappers, etc.); the cdi-module's CDI Extension then leaves the
 * injection point unsatisfied so CDI's own deployment validation
 * surfaces the offending type.
 */
public abstract class MockitoMockFactory {

    /**
     * Suppressed-instantiation constructor. The class is
     * {@code abstract} so direct {@code new} is impossible; the
     * explicit declaration silences {@code javadoc -doclint:all} on
     * the otherwise synthesized default constructor.
     */
    protected MockitoMockFactory() {
    }

    /**
     * Create a Mockito mock of the given type, or {@code null} when
     * the type is unmockable.
     *
     * @param rawType the type to mock
     * @param <T>     the mocked type
     * @return a fresh Mockito mock, or {@code null} when Mockito
     *         throws while trying to create one
     */
    public static <T> T create(Class<T> rawType) {
        try {
            return Mockito.mock(rawType);
        } catch (RuntimeException unmockable) {
            return null;
        }
    }
}
