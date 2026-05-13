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
package org.os890.jawelte.tests.dbtestdata.scenario60;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.dbtestdata.api.DbDiff;

class Scenario60Test {

    private Connection connection;

    @BeforeEach
    void openConnection() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:scenario60;MODE=LEGACY;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE FLAG_ROW (ID INT PRIMARY KEY, FLAG VARCHAR(8))");
            // PostgreSQL's BOOLEAN export shape: textual 't' / 'f'.
            statement.execute("INSERT INTO FLAG_ROW VALUES (1, 't')");
            statement.execute("INSERT INTO FLAG_ROW VALUES (2, 'f')");
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
    void postgresTfStringsNormaliseToTheBuiltInBooleanBuckets() {
        // 't' / 'f' (PostgreSQL's BOOLEAN export form) are in the
        // built-in truthy / falsy lists alongside the existing
        // true/1/yes/y/on and false/0/no/n/off entries — case
        // matching is case-insensitive, so 't' / 'T' both work.
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<dataset>"
                + "<FLAG_ROW ID=\"1\" FLAG=\"true\"/>"
                + "<FLAG_ROW ID=\"2\" FLAG=\"false\"/>"
                + "</dataset>";
        DbDiff.forConnection(connection).expectedContent(expected).assertEquals();
    }
}
