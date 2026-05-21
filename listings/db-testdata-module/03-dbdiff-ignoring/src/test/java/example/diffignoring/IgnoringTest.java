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
package example.diffignoring;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.dbtestdata.api.DbDiff;
import org.os890.jawelte.module.dbtestdata.api.DbSeed;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;

/**
 * dbIn seeds rows with real timestamps; the expected dataset uses a
 * sentinel "9999-01-01..." timestamp that obviously doesn't match.
 * DbDiff.ignoring("ORDER_ROW.LAST_MODIFIED") skips that column from
 * the diff so the comparison passes on the actual product values.
 */
@EnableTestBeans
@PersistenceConfig(persistenceUnitName = "ordersPU")
class IgnoringTest {

    @Test
    @Transactional
    void ignoringSkipsTheNoisyColumn() {
        DbSeed.forPersistenceUnit()
                .dataset("orders/dbIn.xml")
                .cleanInsert()
                .execute();

        DbDiff.forPersistenceUnit()
                .ignoring("ORDER_ROW.LAST_MODIFIED")
                .expected("orders/dbExpected.xml")
                .assertEquals();
    }
}
