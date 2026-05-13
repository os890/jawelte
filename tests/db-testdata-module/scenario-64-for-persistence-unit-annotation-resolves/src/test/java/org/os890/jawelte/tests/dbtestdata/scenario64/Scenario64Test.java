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
package org.os890.jawelte.tests.dbtestdata.scenario64;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;

/**
 * The new {@code DbSeed.forPersistenceUnit()} / {@code DbDiff.forPersistenceUnit()}
 * factories consult {@code @PersistenceConfig.persistenceUnitName}
 * on the test class. Two PUs are active inside the
 * {@code @Transactional} service call, which would normally make
 * {@code forCurrentPersistenceUnit()} raise — but the annotation
 * pins the resolution to {@code testPU64A}, so the seed lands in
 * that PU and the matching diff verifies its content.
 */
@EnableTestBeans
@PersistenceConfig(persistenceUnitName = "testPU64A")
public class Scenario64Test {

    @Inject
    private AnnotationDrivenSeedingService annotationDrivenSeedingService;

    public Scenario64Test() {
    }

    @Test
    public void persistenceConfigNameRoutesForPersistenceUnitNoArgToTheNamedPu() {
        long rowsInTestPu64A = annotationDrivenSeedingService.seedWithAnnotationDrivenForPuAndCount();
        assertThat(rowsInTestPu64A).isEqualTo(2);
    }
}
