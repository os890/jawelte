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
package org.os890.jawelte.tests.dbtestdata.scenario53;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.dbtestdata.api.DbDiff;

class Scenario53Test {

    private Connection connection;

    @BeforeEach
    void openConnection() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:scenario53;MODE=LEGACY;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE ITEM (ID INT PRIMARY KEY, PRICE DECIMAL(10,2))");
            statement.execute("INSERT INTO ITEM VALUES (1, 9.99)");
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
    void mismatchSurfacesTheExpressionInTheAssertionMessage() {
        // The predicate evaluates to FALSE for PRICE=9.99 → the engine
        // emits a VALUE_MISMATCH whose expected= carries the raw
        // marker text.
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<dataset>"
                + "<ITEM ID=\"1\" PRICE=\"#{num gt 100}\"/>"
                + "</dataset>";
        var builder = DbDiff.forConnection(connection).expectedContent(expected);
        assertThatThrownBy(builder::assertEquals)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("#{num gt 100}")
                .hasMessageContaining("actual=\"9.99\"");
    }
}
