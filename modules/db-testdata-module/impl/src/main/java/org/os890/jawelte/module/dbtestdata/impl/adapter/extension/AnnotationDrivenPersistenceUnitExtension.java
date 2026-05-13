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
package org.os890.jawelte.module.dbtestdata.impl.adapter.extension;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.dbtestdata.api.port.PersistenceUnitNameSupplier;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;

/**
 * CDI Extension shipped by db-testdata-module. During
 * {@code BeforeBeanDiscovery} - the bootstrap window where
 * {@link TestContext#get()} still resolves - it reads
 * {@link PersistenceConfig#persistenceUnitName()} from the active
 * test class and stores it on this instance. The default
 * {@link PersistenceUnitNameSupplier} CDI bean retrieves the value
 * via {@code BeanManager.getExtension(...)} in its
 * {@code @Initialized(ApplicationScoped.class)} observer.
 *
 * <p>When no {@link TestContext} is active on the calling thread
 * (e.g. {@code @EnableTestBeans(manageContainer=false)} with the user
 * booting the container manually), {@link #capturedName()} stays at
 * its empty default and {@code DbSeed.forPersistenceUnit()} /
 * {@code DbDiff.forPersistenceUnit()} delegate to
 * {@code forCurrentPersistenceUnit()}.
 *
 * <p>Loaded by the CDI runtime via the
 * {@code META-INF/services/jakarta.enterprise.inject.spi.Extension}
 * registration shipped in this module.
 */
public class AnnotationDrivenPersistenceUnitExtension implements Extension {

    private String capturedName = "";

    /** No-arg constructor required by the CDI runtime. */
    public AnnotationDrivenPersistenceUnitExtension() {
    }

    void onBeforeBeanDiscovery(@Observes BeforeBeanDiscovery event) {
        TestContext testContext;
        try {
            testContext = TestContext.get();
        } catch (IllegalStateException notInBootstrap) {
            return;
        }
        PersistenceConfig persistenceConfig = testContext.getTestClass().getAnnotation(PersistenceConfig.class);
        if (persistenceConfig != null) {
            this.capturedName = persistenceConfig.persistenceUnitName();
        }
    }

    /**
     * The captured {@link PersistenceConfig#persistenceUnitName()}
     * value, or the empty string when no annotation was present or
     * no {@code TestContext} was active during
     * {@code BeforeBeanDiscovery}.
     *
     * @return the captured value; never {@code null}
     */
    public String capturedName() {
        return capturedName;
    }
}
