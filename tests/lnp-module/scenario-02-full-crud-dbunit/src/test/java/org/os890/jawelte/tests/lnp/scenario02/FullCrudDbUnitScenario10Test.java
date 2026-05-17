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
package org.os890.jawelte.tests.lnp.scenario02;

import org.junit.jupiter.api.extension.ExtendWith;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;
import org.os890.jawelte.tests.lnp.scenario02.metrics.PerformanceExtension;

/**
 * Numbered copy 10 of {@link AbstractFullCrudDbUnitScenarioTest}.
 * The load-and-performance sweep instantiates this class N=50 times
 * per JVM so the db-testdata-module's @TestControl seed + diff cost
 * is amplified into a measurable signal that lines up next to
 * scenario-01's programmatic baseline.
 */
@ExtendWith(PerformanceExtension.class)
@EnableTestBeans
@PersistenceConfig(persistenceUnitName = "lnpFullCrudDbUnitPU")
public class FullCrudDbUnitScenario10Test extends AbstractFullCrudDbUnitScenarioTest {

    /** Default constructor for JUnit / CDI. */
    public FullCrudDbUnitScenario10Test() {
    }
}
