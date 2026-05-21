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
package example.basepath;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;
import org.os890.jawelte.module.testcontrol.api.TestControl;

/**
 * testDataBasePath="fixtures" applies a prefix to each @TestControl's
 * testData entries — the per-method testData attribute then names just
 * the leaf slug ("m01", "m02"). Without the basePath each method would
 * have to write the full "fixtures/m01" / "fixtures/m02".
 */
@EnableTestBeans
@PersistenceConfig(persistenceUnitName = "itemsPU")
class ItemsTest {

    @Test
    @TestControl(testDataBasePath = "fixtures", testData = "m01")
    void firstMethodReadsFixturesM01() {
    }

    @Test
    @TestControl(testDataBasePath = "fixtures", testData = "m02")
    void secondMethodReadsFixturesM02() {
    }
}
