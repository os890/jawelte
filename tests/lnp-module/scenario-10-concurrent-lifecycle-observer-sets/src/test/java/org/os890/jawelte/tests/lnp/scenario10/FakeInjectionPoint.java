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
import java.lang.reflect.Member;
import java.lang.reflect.Type;
import java.util.Set;

import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;

/** Minimal {@link InjectionPoint} fake exposing only the type + qualifiers the
 *  cdi observer reads. */
public class FakeInjectionPoint implements InjectionPoint {

    private final Type type;

    public FakeInjectionPoint(Type type) {
        this.type = type;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return Set.of();
    }

    @Override
    public Bean<?> getBean() {
        return null;
    }

    @Override
    public Member getMember() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Annotated getAnnotated() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDelegate() {
        return false;
    }

    @Override
    public boolean isTransient() {
        return false;
    }
}
