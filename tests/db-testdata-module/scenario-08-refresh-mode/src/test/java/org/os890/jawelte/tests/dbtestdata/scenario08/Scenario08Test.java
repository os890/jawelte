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
package org.os890.jawelte.tests.dbtestdata.scenario08;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.dbtestdata.api.DbSeed;

class Scenario08Test {

    private Connection connection;

    @BeforeEach
    void openConnection() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:scenario08;MODE=LEGACY;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE CUSTOMER (ID INT PRIMARY KEY, NAME VARCHAR(64))");
            statement.execute("INSERT INTO CUSTOMER VALUES (1, 'Alice')");
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
    void refreshUpsertsByPrimaryKey() throws Exception {
        // Mix of existing (ID 1) and new (ID 2) rows. refresh()
        // upserts: existing rows UPDATE, new rows INSERT — no DELETE
        // step fires.
        String dataset = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<dataset>"
                + "<CUSTOMER ID=\"1\" NAME=\"Alice (renamed)\"/>"
                + "<CUSTOMER ID=\"2\" NAME=\"Bob\"/>"
                + "</dataset>";

        DbSeed.forConnection(connection)
                .datasetContent(dataset)
                .refresh()
                .execute();

        Map<Integer, String> namesById = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT ID, NAME FROM CUSTOMER ORDER BY ID")) {
            while (resultSet.next()) {
                namesById.put(resultSet.getInt(1), resultSet.getString(2));
            }
        }
        assertThat(namesById).containsExactly(
                Map.entry(1, "Alice (renamed)"),
                Map.entry(2, "Bob"));
    }
}
