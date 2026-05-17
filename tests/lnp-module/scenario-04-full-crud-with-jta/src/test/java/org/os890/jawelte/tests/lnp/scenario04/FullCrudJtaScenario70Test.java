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
package org.os890.jawelte.tests.lnp.scenario04;

import org.junit.jupiter.api.extension.ExtendWith;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.tests.lnp.scenario04.metrics.PerformanceExtension;

/**
 * Numbered copy 70 of {@link AbstractFullCrudJtaScenarioTest} - the
 * load-and-performance scenario boots the CDI container, populates
 * the 50-entity domain, and runs the full CRUD method set N=50 times
 * per JVM so bootstrap and class-load costs amplify into a
 * measurable signal.
 */
@ExtendWith(PerformanceExtension.class)
@EnableTestBeans
public class FullCrudJtaScenario70Test extends AbstractFullCrudJtaScenarioTest {

    /** Default constructor for JUnit / CDI. */
    public FullCrudJtaScenario70Test() {
    }
}
