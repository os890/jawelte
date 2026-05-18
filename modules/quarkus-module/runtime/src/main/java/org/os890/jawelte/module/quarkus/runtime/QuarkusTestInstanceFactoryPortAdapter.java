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

import jakarta.annotation.Priority;

import org.os890.jawelte.core.api.port.TestInstanceFactoryPort;

/**
 * quarkus-module's higher-priority {@link TestInstanceFactoryPort}
 * implementation. Returns {@code null} so {@code EnableTestBeans.Proxy}'s
 * createTestInstance falls through to the configured container-extension
 * delegate (Quarkus's {@code QuarkusTestExtension}, pre-set in this
 * module's {@code META-INF/microprofile-config.properties}). Stepping
 * the cdi-module port-adapter out of the way prevents it from
 * bootstrapping a second CDI container — Quarkus's ArC owns
 * container lifecycle under {@code @QuarkusTest}-equivalent runs.
 *
 * <p>Stays JUnit-free intentionally so quarkus-module/runtime does
 * not depend on JUnit at all; the actual delegation to JUnit's
 * {@code TestInstanceFactory} happens in
 * {@code EnableTestBeans.Proxy} against the
 * {@code ContainerExtensionDelegate} stored in
 * {@code TestContext} metadata.
 */
@Priority(100)
public class QuarkusTestInstanceFactoryPortAdapter implements TestInstanceFactoryPort {

    /** No-arg constructor required by {@code ServiceLoader}. */
    public QuarkusTestInstanceFactoryPortAdapter() {
    }

    @Override
    public Object createInstance(Class<?> testClass) {
        return null;
    }
}
