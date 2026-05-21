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
package example.updatephase;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;
import org.os890.jawelte.module.testcontrol.api.TestControl;

/**
 * Full @TestControl pipeline: dbIn/ seeds, dbUpdate/ runs UPDATE
 * statements (clean-insert vs update is the only difference between
 * the two phases), dbExpected/ verifies the post-method state. The
 * test method body is empty — the dbUpdate happens in beforeEach
 * AFTER the dbIn seed, and dbExpected verifies in afterEach.
 */
@EnableTestBeans
@PersistenceConfig(persistenceUnitName = "productsPU")
class ProductsTest {

    @Test
    @TestControl(testData = "testdata/products")
    void dbInThenDbUpdateThenVerifyAgainstDbExpected() {
    }
}
