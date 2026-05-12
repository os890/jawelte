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
package org.os890.jawelte.tests.ejb.scenario25;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.spi.BeanManager;

import org.os890.jawelte.module.ejb.api.port.EjbAnnotationMapper;

/**
 * Test-only TERMINAL {@link EjbAnnotationMapper} that replaces the
 * shipping {@code DefaultEjbAnnotationMapper} via priority sorting
 * ({@code Integer.MAX_VALUE - 1} beats {@code Integer.MAX_VALUE} in
 * a priority-ascending sort, so the chain resolution treats this
 * mapper as the active terminal). Records every class it observes —
 * the scenario asserts that the additional mapper's empty-list claim
 * really suppresses the terminal, i.e. {@link ClaimedByEmptyList}
 * never reaches this recorder.
 */
@Priority(Integer.MAX_VALUE - 1)
public class TestScenarioRecordingTerminal implements EjbAnnotationMapper {

    /** Classes the terminal observed during the bootstrap. */
    public static final Set<Class<?>> OBSERVED = ConcurrentHashMap.newKeySet();

    /** Required public no-arg constructor. */
    public TestScenarioRecordingTerminal() {
    }

    @Override
    public boolean isAdditionalMapper() {
        return false;
    }

    @Override
    public List<Annotation> mapBeanMetadata(Class<?> beanClass, BeanManager beanManager) {
        OBSERVED.add(beanClass);
        return null;
    }
}
