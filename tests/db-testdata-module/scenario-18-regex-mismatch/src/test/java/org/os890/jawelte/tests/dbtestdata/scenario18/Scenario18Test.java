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
package org.os890.jawelte.tests.dbtestdata.scenario18;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.dbtestdata.api.DbDiff;

class Scenario18Test {

    private Connection connection;

    @BeforeEach
    void openConnection() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:scenario18;MODE=LEGACY;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE CUSTOMER (ID INT PRIMARY KEY, CODE VARCHAR(64))");
            statement.execute("INSERT INTO CUSTOMER VALUES (1, 'abc')");
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
    void regexThatDoesNotMatchActualValueSurfacesAsValueMismatch() {
        // Regex [A-Z]+ does not match "abc"; the diff carries the
        // raw marker text in the expected= field so the test author
        // sees what they wrote.
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<dataset>"
                + "<CUSTOMER ID=\"1\" CODE=\"[MATCH:[A-Z]+]\"/>"
                + "</dataset>";
        assertThatThrownBy(() ->
                DbDiff.forConnection(connection)
                        .expectedContent(expected)
                        .assertEquals())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("CUSTOMER[0].CODE")
                .hasMessageContaining("expected=\"[MATCH:[A-Z]+]\"")
                .hasMessageContaining("actual=\"abc\"");
    }
}
