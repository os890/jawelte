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
package example.factory;

import org.os890.jawelte.core.api.port.TestBeanContainerPort;
import org.os890.jawelte.core.api.port.TestContext;

/**
 * Stub TestBeanContainerPort needed only because core's
 * DelegatingJUnitExtension requires exactly one impl. Real CDI runtimes
 * (cdi-module's CdiTestBeanContainer, quarkus-module's adapter) bootstrap
 * a container here; this stub does nothing — the listing is about
 * TestInstanceFactoryPort, not container bootstrap.
 */
public class NoopContainerPort implements TestBeanContainerPort {

    @Override
    public void beforeAll(TestContext testContext) {
    }

    @Override
    public void postProcessTestInstance(TestContext testContext, Object testInstance) {
    }

    @Override
    public void beforeEach(TestContext testContext) {
    }

    @Override
    public void afterEach(TestContext testContext) {
    }

    @Override
    public void afterAll(TestContext testContext) {
    }
}
