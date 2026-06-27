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
package org.os890.jawelte.tests.dbtestdata.scenario68;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.dbtestdata.api.DbDiff;

/**
 * A {@code ${...}} expression in a dataset template that legitimately
 * contains a {@code '}'} must be interpolated intact. The brace can sit
 * inside an EL string literal (here via {@code String.concat('}')}) or
 * inside a nested map literal ({@code {'a':1}}). A naive "first closing
 * brace wins" scan truncates such an expression and hands the EL parser
 * a malformed fragment, which throws at parse time; the brace-aware
 * scan keeps the whole expression together.
 */
class Scenario68Test {

    private Connection connection;

    @BeforeEach
    void openConnection() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:scenario68;MODE=LEGACY;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE EVENT (ID INT PRIMARY KEY, LABEL VARCHAR(64))");
        }
    }

    private void insert(int id, String label) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO EVENT VALUES (" + id + ", '" + label + "')");
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
    void stringLiteralContainingClosingBraceIsNotTruncated() throws Exception {
        insert(1, "x}");
        // The '}' lives inside the EL string literal argument to concat;
        // the expression interpolates to "x}".
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<dataset>"
                + "<EVENT ID=\"1\" LABEL=\"${name.concat('}')}\"/>"
                + "</dataset>";
        DbDiff.forConnection(connection)
                .expectedContent(expected)
                .withValues(Map.of("name", "x"))
                .assertEquals();
    }

    @Test
    void nestedMapLiteralBracesAreNotTruncated() throws Exception {
        insert(2, "1");
        // The inner '}' closes the EL map literal, not the expression;
        // {'a':1}['a'] evaluates to 1.
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<dataset>"
                + "<EVENT ID=\"2\" LABEL=\"${ {'a':1}['a'] }\"/>"
                + "</dataset>";
        DbDiff.forConnection(connection)
                .expectedContent(expected)
                .assertEquals();
    }
}
