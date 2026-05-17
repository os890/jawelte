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
package org.os890.jawelte.tests.lnp.scenario07;

import org.junit.jupiter.api.extension.ExtendWith;
import org.os890.jawelte.module.jaxrs.api.EnableJaxRs;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;
import org.os890.jawelte.tests.lnp.scenario07.metrics.PerformanceExtension;

/**
 * Numbered copy 23 of {@link AbstractFullCrudGatlingScenarioTest}.
 * The LNP sweep instantiates this class N=50 times per JVM so the
 * cost of one full CRUD HTTP roundtrip per class is amplified into a
 * measurable signal that lines up next to scenarios 01-05 in the
 * report.
 */
@ExtendWith(PerformanceExtension.class)
@EnableJaxRs(restResources = CustomerResource.class)
@PersistenceConfig(persistenceUnitName = "lnpFullCrudGatlingPU")
public class FullCrudGatlingScenario23Test extends AbstractFullCrudGatlingScenarioTest {

    /** Default constructor for JUnit / CDI. */
    public FullCrudGatlingScenario23Test() {
    }
}
