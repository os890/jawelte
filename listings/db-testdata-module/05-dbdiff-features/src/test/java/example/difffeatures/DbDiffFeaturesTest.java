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
package example.difffeatures;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.dbtestdata.api.DbDiff;
import org.os890.jawelte.module.dbtestdata.api.DbSeed;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;

/**
 * Demonstrates the DbDiff builder methods that listing 03
 * (dbdiff-ignoring) didn't cover:
 *
 * <ul>
 *   <li>{@link DbDiff.Builder#assertRowCount} — quick count-only check</li>
 *   <li>{@link DbDiff.Builder#subsetOnly} — DB may contain extra rows;
 *       assert only the rows in the expected file</li>
 *   <li>{@link DbDiff.Builder#unorderedTables} — compare a table as a
 *       multiset (row order doesn't matter)</li>
 *   <li>{@link DbDiff.Builder#withBean} — register a bean for EL
 *       property navigation in the expected dataset</li>
 *   <li>{@link DbDiff.Builder#withFunction} — register a public-static
 *       EL function callable from the expected dataset</li>
 * </ul>
 *
 * <p>Each test method seeds the same three rows then asserts via a
 * different builder option, so the contract surface is exercised
 * end-to-end against a real H2 database.
 */
@EnableTestBeans
@PersistenceConfig(persistenceUnitName = "ordersPU")
class DbDiffFeaturesTest {

    @BeforeEach
    @Transactional
    void seedThreeRows() {
        DbSeed.forPersistenceUnit().dataset("orders/dbIn.xml").cleanInsert().execute();
    }

    @Test
    @Transactional
    void assertRowCountSkipsTheFullDiff() {
        DbDiff.forPersistenceUnit()
                .expectedContent("<dataset/>")   // unused by assertRowCount, but required by the builder shape
                .assertRowCount("ORDER_ROW", 3);
    }

    @Test
    @Transactional
    void subsetOnlyIgnoresExtraRowsInTheDatabase() {
        // The seed dataset has three rows (1 apple, 2 banana, 3 cherry).
        // The expected-subset dataset lists only rows 1 and 2.
        // .subsetOnly() narrows the comparison to the expected rows so the
        // extra row (id=3 cherry) does not register as a difference.
        DbDiff.forPersistenceUnit()
                .expected("orders/dbExpected-subset.xml")
                .subsetOnly()
                .assertEquals();
    }

    @Test
    @Transactional
    void withoutSubsetOnlyTheExtraRowIsAFailure() {
        assertThatThrownBy(() ->
                DbDiff.forPersistenceUnit()
                        .expected("orders/dbExpected-subset.xml")
                        .assertEquals())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ORDER_ROW");
    }

    @Test
    @Transactional
    void unorderedTablesIgnoresRowOrder() {
        // dbExpected-unordered.xml lists the same three rows but in
        // order (3, 1, 2). The default diff is row-order sensitive;
        // .unorderedTables("ORDER_ROW") tells the engine to compare
        // ORDER_ROW as a multiset.
        DbDiff.forPersistenceUnit()
                .expected("orders/dbExpected-unordered.xml")
                .unorderedTables("ORDER_ROW")
                .assertEquals();
    }

    @Test
    @Transactional
    void withBeanAndWithFunctionResolveElInTheExpectedDataset() {
        // dbExpected-el.xml contains ${catalog.appleName},
        // ${catalog.bananaName}, ${fn:lower('CHERRY')}. The bean and
        // function registrations resolve those before the diff runs.
        DbDiff.forPersistenceUnit()
                .withBean("catalog", new Catalog())
                .withFunction("fn", "lower", StringFns.class, "lower")
                .expected("orders/dbExpected-el.xml")
                .assertEquals();
    }
}
