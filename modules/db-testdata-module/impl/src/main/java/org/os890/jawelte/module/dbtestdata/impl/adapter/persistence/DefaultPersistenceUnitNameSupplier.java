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

import org.os890.jawelte.module.dbtestdata.api.port.PersistenceUnitNameSupplier;

/**
 * Default {@link PersistenceUnitNameSupplier}: an
 * {@code @ApplicationScoped} CDI bean whose {@link #get()} delegates
 * to {@link CapturedPersistenceUnitNameHolder}. The holder is
 * populated by {@code DbTestDataArcContextContributor} during
 * cdi-module's {@code beforeAll}, before {@code BeanProcessor.process()}
 * runs.
 *
 * <p>Consumers can replace this default by providing their own
 * {@code @Alternative @Priority(N)} bean of type
 * {@link PersistenceUnitNameSupplier}.
 *
 * <p>The previous OWB / Weld path captured the value on a CDI
 * Extension instance and read it back via
 * {@code BeanManager.getExtension(...)}. ArC does not support
 * {@code BeanManager.getExtension(...)} (it throws
 * {@code UnsupportedOperationException}), so the value flow is now:
 * contributor → static holder → bean.
 */
@ApplicationScoped
public class DefaultPersistenceUnitNameSupplier implements PersistenceUnitNameSupplier {

    /** No-arg constructor required by the CDI normal-scope proxy. */
    public DefaultPersistenceUnitNameSupplier() {
    }

    @Override
    public String get() {
        return CapturedPersistenceUnitNameHolder.get();
    }
}
