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
package org.os890.jawelte.tests.dbtestdata.scenario03;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.dbtestdata.api.DbSeed;

class Scenario03Test {

    private Connection connection;

    @BeforeEach
    void openConnection() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:scenario03;MODE=LEGACY;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement statement = connection.createStatement()) {
            // Two tables that reference each other. The FKs are
            // added after both tables exist; pre-existing rows form
            // a circular reference. cleanInsert() cannot satisfy
            // either DELETE direction: DELETE B first fails because
            // A.B_ID references B; DELETE A first fails because
            // B.A_ID references A.
            statement.execute("CREATE TABLE A (ID INT PRIMARY KEY, B_ID INT)");
            statement.execute("CREATE TABLE B (ID INT PRIMARY KEY, A_ID INT)");
            statement.execute("INSERT INTO A VALUES (1, NULL)");
            statement.execute("INSERT INTO B VALUES (1, 1)");
            statement.execute("UPDATE A SET B_ID = 1 WHERE ID = 1");
            statement.execute(
                    "ALTER TABLE A ADD CONSTRAINT FK_A_B FOREIGN KEY (B_ID) REFERENCES B(ID)");
            statement.execute(
                    "ALTER TABLE B ADD CONSTRAINT FK_B_A FOREIGN KEY (A_ID) REFERENCES A(ID)");
        }
    }

    @AfterEach
    void closeConnection() throws Exception {
        if (connection != null) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP ALL OBJECTS");
            }
            connection.close();
        }
    }

    @Test
    void cleanInsertFailsBecauseTheDeletePhaseHitsACircularConstraint() {
        // The dataset is irrelevant — cleanInsert never gets past
        // the DELETE phase because neither A nor B can be emptied
        // while the other still references its rows.
        String dataset = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<dataset>"
                + "<A ID=\"2\" B_ID=\"2\"/>"
                + "<B ID=\"2\" A_ID=\"2\"/>"
                + "</dataset>";
        assertThatThrownBy(() ->
                DbSeed.forConnection(connection)
                        .datasetContent(dataset)
                        .cleanInsert()
                        .execute())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("[DbSeed]");
    }

    @Test
    void refreshSucceedsBecauseItOnlyUpdatesExistingRows() throws Exception {
        // refresh() upserts row-by-row: existing rows UPDATE, new
        // rows INSERT — no DELETE step. The first row is an UPDATE
        // (A.ID == 1 already exists), so the FK reference does not
        // break.
        String dataset = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<dataset>"
                + "<A ID=\"1\" B_ID=\"1\"/>"
                + "<B ID=\"1\" A_ID=\"1\"/>"
                + "</dataset>";
        DbSeed.forConnection(connection)
                .datasetContent(dataset)
                .refresh()
                .execute();
        try (Statement statement = connection.createStatement();
                ResultSet count = statement.executeQuery("SELECT COUNT(*) FROM A")) {
            count.next();
            assertThat(count.getInt(1)).isEqualTo(1);
        }
    }
}
