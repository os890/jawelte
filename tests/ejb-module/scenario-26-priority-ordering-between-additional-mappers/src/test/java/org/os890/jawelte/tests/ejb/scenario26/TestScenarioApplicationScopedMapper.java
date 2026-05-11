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
package org.os890.jawelte.tests.ejb.scenario26;

import java.lang.annotation.Annotation;
import java.util.List;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.BeanManager;

import org.os890.jawelte.module.ejb.api.port.EjbAnnotationMapper;

/**
 * Higher {@code @Priority} value (200) — sorts AFTER
 * {@code TestScenarioRequestScopedMapper}. Would claim
 * {@link MarkerSingleton} with {@code [@ApplicationScoped]} but
 * never gets the chance because the lower-priority mapper claimed
 * it first. Returning {@code null} for every other class avoids
 * accidentally interfering with the rest of the test classpath.
 */
@Priority(200)
public class TestScenarioApplicationScopedMapper implements EjbAnnotationMapper {

    /** Required public no-arg constructor. */
    public TestScenarioApplicationScopedMapper() {
    }

    @Override
    public List<Annotation> mapBeanMetadata(Class<?> beanClass, BeanManager beanManager) {
        if (beanClass.equals(MarkerSingleton.class)) {
            return List.of(ApplicationScoped.Literal.INSTANCE);
        }
        return null;
    }
}
