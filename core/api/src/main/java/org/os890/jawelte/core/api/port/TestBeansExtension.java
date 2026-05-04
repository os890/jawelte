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
package org.os890.jawelte.core.api.port;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

/**
 * Bootstrap SPI loaded by the nested
 * {@code org.os890.jawelte.core.api.EnableTestBeans.Proxy} via
 * {@code ServiceLoader}. The proxy is the only consumer of this SPI;
 * everything the implementation forwards to in the core (namely
 * {@link TestBeanContainerPort} and {@link TestModuleLifecyclePort})
 * receives a {@link TestContext} instead of the JUnit
 * {@code ExtensionContext}, keeping the downstream SPI JUnit-free.
 *
 * <p>This is the only port that takes {@code ExtensionContext}; it is
 * the JUnit boundary.
 *
 * <p>Exactly one implementation must be on the classpath. Zero or more
 * than one implementation triggers an {@link IllegalStateException}
 * thrown by the proxy on its first callback.
 */
public interface TestBeansExtension
        extends BeforeAllCallback,
                BeforeEachCallback,
                TestInstancePostProcessor,
                AfterEachCallback,
                AfterAllCallback {
}
