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
package org.os890.jawelte.module.datasource.impl.adapter.lifecycle;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.spi.CDI;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;
import org.os890.jawelte.module.datasource.impl.adapter.extension.DataSourceDefinitionCdiExtension;

/**
 * {@link TestModuleLifecyclePort} adapter shipped by
 * datasource-module/impl. Releases the data sources when the test class
 * is done.
 *
 * <p><b>Building is not done here, deliberately.</b> A lifecycle port's
 * {@code beforeAll} runs after {@code TestBeanContainerPort.beforeAll},
 * which is what starts the CDI container — so anything built here would
 * arrive after {@code @Initialized(ApplicationScoped.class)} has already
 * fired, and a startup observer using a declared data source would have
 * failed already. Schema migration, readiness probes and cache warm-up
 * all live in that window. The build therefore happens in
 * {@code DataSourceDefinitionCdiExtension}'s
 * {@code AfterDeploymentValidation} observer, which runs before the
 * application context starts — the order a real container establishes a
 * {@code @DataSourceDefinition} in.
 *
 * <p>What is left for this adapter is the other end: unbinding the JNDI
 * entries and closing what can be closed, once the class is finished.
 * That has to be driven from the test lifecycle, because the CDI
 * container is shut down by the bean-container port right after.
 *
 * <p><b>Priority.</b> {@code @Priority(150)} — after scope-module (100)
 * and before jpa-module (200). For teardown the order is reversed
 * (LIFO), so the data sources outlive jpa-module's own cleanup, which is
 * the ordering a persistence unit resolving a declared data source will
 * need.
 *
 * <p><b>No-op unless something was built.</b> With no
 * {@code @DataSourceDefinition} anywhere the extension holds nothing
 * and this returns after the one lookup it needs to ask.
 */
@Priority(150)
public class DataSourceLifecycleAdapter implements TestModuleLifecyclePort {

    /** No-arg constructor required by SPI {@code ServiceLoader} lookup. */
    public DataSourceLifecycleAdapter() {
    }

    @Override
    public void afterAll(TestContext testContext) {
        DataSourceDefinitionCdiExtension extension = CDI.current().getBeanManager()
                .getExtension(DataSourceDefinitionCdiExtension.class);
        if (!extension.hasBuiltDataSources()) {
            return;
        }
        IllegalStateException releaseFailures = new IllegalStateException(
                "Failed to release one or more declared DataSource(s)");
        extension.releaseAll(releaseFailures);
        if (releaseFailures.getSuppressed().length > 0) {
            throw releaseFailures;
        }
    }
}
