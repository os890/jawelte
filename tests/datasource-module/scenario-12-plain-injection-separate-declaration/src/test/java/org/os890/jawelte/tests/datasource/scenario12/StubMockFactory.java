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
package org.os890.jawelte.tests.datasource.scenario12;

import java.lang.reflect.Proxy;

import jakarta.annotation.Priority;

import org.os890.jawelte.module.cdi.api.port.MockFactory;

/**
 * A {@link MockFactory} that always succeeds, replacing the shipped
 * Mockito one for this scenario.
 *
 * <p>This is what makes the scenario a reproduction rather than a
 * coincidence. The collision it guards against only exists if auto-mock
 * actually produces a mock for the {@code DataSource} injection point —
 * and measured on this JDK, {@code Mockito.mock(...)} refuses the type
 * and {@code MockitoMockFactory} answers {@code null} when it does,
 * which silently skips the auto-mock and hides the bug. That is exactly
 * why #124 reproduced for the reporter and not here.
 *
 * <p>{@code @Priority(100)} beats the shipped factory's
 * {@code Integer.MAX_VALUE}.
 */
@Priority(100)
public class StubMockFactory implements MockFactory {

    /** No-arg constructor required by {@code ServiceLoader}. */
    public StubMockFactory() {
    }

    @Override
    public <T> T create(Class<T> rawType) {
        if (!rawType.isInterface()) {
            return null;
        }
        return rawType.cast(Proxy.newProxyInstance(
                rawType.getClassLoader(),
                new Class<?>[] {rawType},
                (proxy, method, args) -> null));
    }
}
