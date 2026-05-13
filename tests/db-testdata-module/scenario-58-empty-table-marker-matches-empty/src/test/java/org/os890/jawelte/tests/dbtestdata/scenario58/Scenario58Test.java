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
package org.os890.jawelte.tests.dbtestdata.scenario58;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.dbtestdata.api.DbDiff;

class Scenario58Test {

    private Connection connection;

    @BeforeEach
    void openConnection() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:scenario58;MODE=LEGACY;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE CUSTOMER (ID INT PRIMARY KEY, NAME VARCHAR(64))");
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
    void zeroAttributeElementAssertsTheTableIsEmpty() {
        // <CUSTOMER/> in the expected dataset asserts "CUSTOMER has
        // zero rows in the database"; DbUnit's FlatXmlDataSetBuilder
        // silently drops zero-attribute elements, so the engine
        // detects them via the SAX line-locator pass.
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<dataset>"
                + "<CUSTOMER/>"
                + "</dataset>";
        DbDiff.forConnection(connection).expectedContent(expected).assertEquals();
    }
}
