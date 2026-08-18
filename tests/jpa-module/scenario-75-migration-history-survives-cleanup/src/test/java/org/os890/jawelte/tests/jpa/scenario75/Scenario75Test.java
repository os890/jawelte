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
package org.os890.jawelte.tests.jpa.scenario75;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Per-method cleanup empties every base table it finds, which is what
 * lets it reach join tables and audit logs the metamodel does not
 * describe. A schema-migration tool's history table lives in the same
 * schema and is reached the same way - but those rows are not test
 * data. They record what has already been applied, and emptying them
 * while leaving the DDL they describe leaves the next migration run in
 * front of a schema no history remembers.
 *
 * <p>The two methods are ordered because the cleanup boundary between
 * them <em>is</em> the subject. Both assertions matter: without the
 * second, a cleanup that had stopped working altogether would pass.
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Scenario75Test {

    @Inject
    SchemaHistory schemaHistory;

    @Test
    @Order(1)
    void theMigrationRunsAndAnOrdinaryTableIsWritten() {
        schemaHistory.migrate();
        schemaHistory.writeNote(new Note(1L, "test data"));

        assertThat(schemaHistory.appliedCount()).isEqualTo(1L);
        assertThat(schemaHistory.noteCount()).isEqualTo(1L);
    }

    @Test
    @Order(2)
    void cleanupEmptiesTheOrdinaryTableAndLeavesTheHistoryAlone() {
        assertThat(schemaHistory.noteCount())
                .as("cleanup still has to empty ordinary tables - excluding everything is not the fix")
                .isZero();
        assertThat(schemaHistory.appliedCount())
                .as("the migration history records what was applied; the DDL it describes is still there")
                .isEqualTo(1L);
    }
}
