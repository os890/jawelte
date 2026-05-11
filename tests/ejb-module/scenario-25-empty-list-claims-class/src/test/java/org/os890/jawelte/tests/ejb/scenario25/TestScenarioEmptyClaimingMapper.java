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
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.spi.BeanManager;

import org.os890.jawelte.module.ejb.api.port.EjbAnnotationMapper;

/**
 * Returns an empty {@link List} for {@link ClaimedByEmptyList} —
 * claiming the class without contributing any annotations. The
 * default mapper is NOT consulted for the claimed class; counter
 * {@link #DEFAULT_MAPPER_RAN} records whether the default observed
 * the class (it must stay at zero for the assertion that the empty
 * claim suppresses the fallback). Returns {@code null} for every
 * other class.
 */
@Priority(Integer.MAX_VALUE - 100)
public class TestScenarioEmptyClaimingMapper implements EjbAnnotationMapper {

    /**
     * Incremented every time the default mapper observes the
     * claimed class. The empty-list claim should leave this at 0;
     * a regression would let the default run and bump the counter.
     */
    public static final AtomicInteger DEFAULT_MAPPER_RAN = new AtomicInteger();

    /** Required public no-arg constructor. */
    public TestScenarioEmptyClaimingMapper() {
    }

    @Override
    public List<Annotation> mapBeanMetadata(Class<?> beanClass, BeanManager beanManager) {
        if (beanClass.equals(ClaimedByEmptyList.class)) {
            return List.of();
        }
        return null;
    }
}
