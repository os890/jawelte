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
package org.os890.jawelte.tests.lnp.scenario10;

import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.enterprise.inject.spi.configurator.InjectionPointConfigurator;

/** Minimal {@link ProcessInjectionPoint} fake returning a fixed injection point. */
public class FakeProcessInjectionPoint implements ProcessInjectionPoint<Object, Object> {

    private final InjectionPoint injectionPoint;

    public FakeProcessInjectionPoint(InjectionPoint injectionPoint) {
        this.injectionPoint = injectionPoint;
    }

    @Override
    public InjectionPoint getInjectionPoint() {
        return injectionPoint;
    }

    @Override
    public void setInjectionPoint(InjectionPoint injectionPoint) {
        throw new UnsupportedOperationException();
    }

    @Override
    public InjectionPointConfigurator configureInjectionPoint() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addDefinitionError(Throwable t) {
        throw new UnsupportedOperationException();
    }
}
