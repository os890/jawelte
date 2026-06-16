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
package org.os890.jawelte.module.dbtestdata.impl.adapter.contributor;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.impl.spi.ArcContextContributor;
import org.os890.jawelte.module.dbtestdata.impl.adapter.persistence.CapturedPersistenceUnitNameHolder;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;

import io.quarkus.arc.processor.BeanProcessor;

/**
 * db-testdata-module's {@link ArcContextContributor}. Captures
 * {@link PersistenceConfig#persistenceUnitName()} from the active
 * test class into {@link CapturedPersistenceUnitNameHolder} before
 * {@code BeanProcessor.process()} runs, so the
 * {@code DefaultPersistenceUnitNameSupplier} CDI bean can read it on
 * demand without going through {@code BeanManager.getExtension(...)}
 * (which ArC does not support).
 *
 * <p>Replaces the previous {@code AnnotationDrivenPersistenceUnitExtension}
 * portable CDI Extension.
 *
 * <p>Discovered via
 * {@code META-INF/services/org.os890.jawelte.module.cdi.impl.spi.ArcContextContributor}.
 */
public class DbTestDataArcContextContributor implements ArcContextContributor {

    /** No-arg constructor required by {@code ServiceLoader}. */
    public DbTestDataArcContextContributor() {
    }

    @Override
    public void contribute(TestContext testContext, BeanProcessor.Builder builder) {
        PersistenceConfig config = testContext.getTestClass().getAnnotation(PersistenceConfig.class);
        CapturedPersistenceUnitNameHolder.set(
                config == null ? "" : config.persistenceUnitName());
    }
}
