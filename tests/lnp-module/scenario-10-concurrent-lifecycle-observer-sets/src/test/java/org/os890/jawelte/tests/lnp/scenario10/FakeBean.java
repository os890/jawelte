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

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;

/** Minimal {@link Bean} fake exposing only the bean types the spring-data
 *  {@code onProcessBean} observer reads. */
public class FakeBean implements Bean<Object> {

    private final Set<Type> types;

    public FakeBean(Set<Type> types) {
        this.types = types;
    }

    @Override
    public Set<Type> getTypes() {
        return types;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return Set.of();
    }

    @Override
    public Class<? extends Annotation> getScope() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return Set.of();
    }

    @Override
    public boolean isAlternative() {
        return false;
    }

    @Override
    public Class<?> getBeanClass() {
        return Object.class;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return Set.of();
    }

    @Override
    public Object create(CreationalContext<Object> creationalContext) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void destroy(Object instance, CreationalContext<Object> creationalContext) {
        throw new UnsupportedOperationException();
    }
}
