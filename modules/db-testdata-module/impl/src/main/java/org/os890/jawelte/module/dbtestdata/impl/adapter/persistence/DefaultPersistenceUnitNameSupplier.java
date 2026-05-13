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
package org.os890.jawelte.module.dbtestdata.impl.adapter.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeanManager;

import org.os890.jawelte.module.dbtestdata.api.port.PersistenceUnitNameSupplier;
import org.os890.jawelte.module.dbtestdata.impl.adapter.extension.AnnotationDrivenPersistenceUnitExtension;

/**
 * Default {@link PersistenceUnitNameSupplier}: an
 * {@code @ApplicationScoped} CDI bean populated from
 * {@link AnnotationDrivenPersistenceUnitExtension}'s captured value
 * during the {@code @Initialized(ApplicationScoped.class)} observer.
 * The bean serves as the per-CDI-container cache for the
 * persistence-unit-name read out of the test class's
 * {@code @PersistenceConfig} annotation.
 *
 * <p>Consumers can replace this default by providing their own
 * {@code @Alternative @Priority(N)} bean of type
 * {@link PersistenceUnitNameSupplier}.
 */
@ApplicationScoped
public class DefaultPersistenceUnitNameSupplier implements PersistenceUnitNameSupplier {

    private String name = "";

    /** No-arg constructor required by the CDI normal-scope proxy. */
    public DefaultPersistenceUnitNameSupplier() {
    }

    @Override
    public String get() {
        return name;
    }

    void onApplicationInitialized(
            @Observes @Initialized(ApplicationScoped.class) Object event,
            BeanManager beanManager) {
        AnnotationDrivenPersistenceUnitExtension extension =
                beanManager.getExtension(AnnotationDrivenPersistenceUnitExtension.class);
        if (extension != null) {
            this.name = extension.capturedName();
        }
    }
}
