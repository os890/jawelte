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

import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.ProcessBean;

/** Minimal {@link ProcessBean} fake returning a fixed bean. */
public class FakeProcessBean implements ProcessBean<Object> {

    private final Bean<Object> bean;

    public FakeProcessBean(Bean<Object> bean) {
        this.bean = bean;
    }

    @Override
    public Bean<Object> getBean() {
        return bean;
    }

    @Override
    public Annotated getAnnotated() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addDefinitionError(Throwable t) {
        throw new UnsupportedOperationException();
    }
}
