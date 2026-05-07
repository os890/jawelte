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
package org.os890.jawelte.module.cdi.api.port;

import org.os890.jawelte.core.api.port.TestContext;

/**
 * Factory used by cdi-module's CDI Extension to instantiate the
 * mock object that backs an auto-mocked synthetic bean. The default
 * implementation in {@code cdi-module/impl} wraps {@code Mockito.mock(...)};
 * users plug in EasyMock, JMockit, or a hand-rolled stub by shipping
 * an alternative provider with a lower {@code @Priority} value.
 *
 * <p>Discovered via {@code ServiceLoader} and selected by
 * {@link TestContext#loadService(Class)}, which routes the priority
 * sort through the active
 * {@link org.os890.jawelte.core.api.port.ServicePriorityResolver}.
 *
 * <p>Implementations must return {@code null} (rather than throw)
 * when the requested type is not mockable on the chosen runtime —
 * for example, certain bootstrap-loaded JDK classes that bytecode
 * subclassing cannot extend. The Extension treats {@code null} as
 * "skip auto-mock for this type" so CDI's deployment validation
 * surfaces the offending injection point directly.
 */
public interface MockFactory {

    /**
     * Create a fresh mock instance of the given type, or {@code null}
     * when the type cannot be mocked on this runtime.
     *
     * @param rawType the type to mock
     * @param <T>     the mocked type
     * @return a fresh mock, or {@code null} when the type is unmockable
     */
    <T> T create(Class<T> rawType);
}
