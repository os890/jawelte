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
package org.os890.jawelte.tests.ejb.scenario27;

import java.lang.annotation.Annotation;
import java.util.List;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.spi.BeanManager;

import org.os890.jawelte.module.ejb.api.port.EjbAnnotationMapper;

/**
 * Test-only terminal mapper that returns {@code null} for every
 * class — simulates "no useful mapper on the classpath". Its
 * {@code @Priority(Integer.MAX_VALUE - 1)} sorts BEFORE the
 * shipping {@code DefaultEjbAnnotationMapper} at
 * {@code @Priority(Integer.MAX_VALUE)}, so chain resolution
 * accepts this one as the active terminal and ignores the
 * shipping default. The scenario then verifies that
 * {@code EjbAnnotationExtension} still surfaces EJB-annotated
 * classes via the stereotype declarations alone (no NPE, no
 * failed bootstrap).
 */
@Priority(Integer.MAX_VALUE - 1)
public class TestScenarioNoOpTerminal implements EjbAnnotationMapper {

    /** Required public no-arg constructor. */
    public TestScenarioNoOpTerminal() {
    }

    @Override
    public boolean isAdditionalMapper() {
        return false;
    }

    @Override
    public List<Annotation> mapBeanMetadata(Class<?> beanClass, BeanManager beanManager) {
        return null;
    }
}
