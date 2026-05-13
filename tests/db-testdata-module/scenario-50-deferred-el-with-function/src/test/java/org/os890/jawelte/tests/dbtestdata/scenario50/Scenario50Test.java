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
package org.os890.jawelte.tests.dbtestdata.scenario50;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.dbtestdata.api.DbDiff;

class Scenario50Test {

    private Connection connection;

    @BeforeEach
    void openConnection() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:scenario50;MODE=LEGACY;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE PRODUCT (ID INT PRIMARY KEY, PRICE DECIMAL(10,2))");
            statement.execute("INSERT INTO PRODUCT VALUES (1, 9.99)");
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
    void registeredStaticFunctionIsInvokedDuringPredicateEvaluation() {
        // withFunction registers a static method; #{fn:name(value)}
        // invokes it against the actual cell value.
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<dataset>"
                + "<PRODUCT ID=\"1\" PRICE=\"#{fn:isPositive(value)}\"/>"
                + "</dataset>";
        DbDiff.forConnection(connection)
                .expectedContent(expected)
                .withFunction("fn", "isPositive", PriceValidators.class, "isPositive")
                .assertEquals();
    }

    public static class PriceValidators {

        public static boolean isPositive(Object value) {
            return value != null && Double.parseDouble(value.toString()) > 0;
        }
    }
}
