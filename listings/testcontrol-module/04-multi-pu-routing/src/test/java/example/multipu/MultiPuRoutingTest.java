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
package example.multipu;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.testcontrol.api.TestControl;

/**
 * Two testData entries, each with a "puName:path" prefix. Each entry's
 * dbIn / dbExpected sub-folders route to the named persistence unit:
 * the customers/ folder feeds customersPU, the orders/ folder feeds
 * ordersPU. dbExpected on each side verifies the rows landed in the
 * right database.
 */
@EnableTestBeans
class MultiPuRoutingTest {

    @Test
    @TestControl(testData = {
            "customersPU:testdata/customers",
            "ordersPU:testdata/orders"
    })
    void eachTestDataEntryRoutesToItsOwnPersistenceUnit() {
    }
}
