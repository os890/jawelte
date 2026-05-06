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
package org.os890.jawelte.tests.core.proxymultiprovider;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.os890.jawelte.core.api.port.TestBeansExtension;

public class SecondTestBeansExtension implements TestBeansExtension {

    public SecondTestBeansExtension() {
    }

    @Override
    public void beforeAll(ExtensionContext context) {
    }

    @Override
    public void beforeEach(ExtensionContext context) {
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
    }

    @Override
    public void afterEach(ExtensionContext context) {
    }

    @Override
    public void afterAll(ExtensionContext context) {
    }
}
