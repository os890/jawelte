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
package org.os890.jawelte.tests.ejb.scenario23;

import java.lang.annotation.Annotation;
import java.util.List;

import jakarta.annotation.Priority;
import jakarta.ejb.Stateful;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.BeanManager;

import org.os890.jawelte.module.ejb.api.port.EjbAnnotationMapper;

/**
 * Test-only additional {@link EjbAnnotationMapper} that claims
 * {@code @jakarta.ejb.Stateful} classes and returns
 * {@code [@Dependent]}. Returns {@code null} for every other class
 * so the default mapper still handles {@code @Singleton} /
 * {@code @Stateless}. Carries
 * {@code @Priority(Integer.MAX_VALUE - 100)} so it runs before the
 * default ({@code @Priority(Integer.MAX_VALUE)}).
 */
@Priority(Integer.MAX_VALUE - 100)
public class TestScenarioStatefulMapper implements EjbAnnotationMapper {

    /** Required public no-arg constructor for {@code ServiceLoader} instantiation. */
    public TestScenarioStatefulMapper() {
    }

    @Override
    public List<Annotation> mapBeanMetadata(Class<?> beanClass, BeanManager beanManager) {
        if (beanClass.isAnnotationPresent(Stateful.class)) {
            return List.of(Dependent.Literal.INSTANCE);
        }
        return null;
    }
}
