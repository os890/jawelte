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

import org.os890.jawelte.core.api.port.TestBeanContainerPort;
import org.os890.jawelte.core.api.port.TestContext;

/**
 * Quarkus-flavour {@link TestBeanContainerPort} adapter. Discovered via
 * {@code ServiceLoader} from {@code cdi-module}'s standard registration
 * key. Coexists on the classpath alongside (but not at the same time as)
 * cdi-module's {@code CdiTestBeanContainer} — the user picks one CDI
 * flavour per test classpath by depending on either
 * {@code jawelte-cdi-module-impl} or {@code jawelte-quarkus-module-runtime}.
 *
 * <p>Quarkus owns the CDI container lifecycle: under {@code @QuarkusTest}
 * jawelte's {@code DelegatingJUnitExtension} auto-skips the
 * {@link #beforeAll(TestContext)} / {@link #afterAll(TestContext)} hooks,
 * so the heavy lifting cdi-module's {@code TestBeansCdiExtension}
 * performs at runtime moves to build time inside
 * {@code modules/quarkus-module/deployment}'s
 * {@code JaweltesQuarkusProcessor}. The per-test-method scope activation
 * is still routed through this adapter so the
 * {@link org.os890.jawelte.core.api.event.BeforeScopeStarted} veto
 * semantics stay identical across both CDI flavours.
 */
public class QuarkusTestBeanContainer implements TestBeanContainerPort {

    /** No-arg constructor required by {@code ServiceLoader}. */
    public QuarkusTestBeanContainer() {
    }

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
