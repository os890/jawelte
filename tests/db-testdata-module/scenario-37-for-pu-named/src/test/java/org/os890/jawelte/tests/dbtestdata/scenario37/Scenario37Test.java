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
package org.os890.jawelte.tests.dbtestdata.scenario37;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

@EnableTestBeans
public class Scenario37Test {

    @Inject
    private NamedPuSeedingService namedPuSeedingService;

    public Scenario37Test() {
    }

    @Test
    public void forPersistenceUnitNameRoutesToTheExplicitlyNamedPersistenceUnit() {
        long rowCount = namedPuSeedingService.seedAndCountWithNamedPu();
        assertThat(rowCount).isEqualTo(3);
    }
}
