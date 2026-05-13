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
package org.os890.jawelte.tests.dbtestdata.scenario62;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.dbtestdata.api.DbDiff;

class Scenario62Test {

    private Connection connection;

    @BeforeEach
    void openConnection() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:scenario62;MODE=LEGACY;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE CUSTOMER (ID INT PRIMARY KEY, NAME VARCHAR(64))");
            statement.execute("CREATE TABLE SALES_ORDER ("
                    + "ID INT PRIMARY KEY, CUSTOMER_ID INT, ITEM VARCHAR(64))");
            // CUSTOMER.ID = 42, but SALES_ORDER.CUSTOMER_ID = 99 — broken FK.
            statement.execute("INSERT INTO CUSTOMER VALUES (42, 'Alice')");
            statement.execute("INSERT INTO SALES_ORDER VALUES (100, 99, 'Widget')");
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
    void labelBindingDivergesAcrossCellsAndSurfacesAsValueMismatch() {
        // First @cust1 occurrence (CUSTOMER.ID) binds to "42".
        // Second occurrence (SALES_ORDER.CUSTOMER_ID) sees "99" —
        // diverges from the binding and emits a VALUE_MISMATCH
        // whose expected= field carries the label name + bound value
        // so the test author sees both names of the disagreement.
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<dataset>"
                + "<CUSTOMER ID=\"@cust1\" NAME=\"Alice\"/>"
                + "<SALES_ORDER ID=\"@order1\" CUSTOMER_ID=\"@cust1\" ITEM=\"Widget\"/>"
                + "</dataset>";
        var builder = DbDiff.forConnection(connection).expectedContent(expected);
        assertThatThrownBy(builder::assertEquals)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("SALES_ORDER[0].CUSTOMER_ID")
                .hasMessageContaining("@cust1 bound to \"42\"")
                .hasMessageContaining("actual=\"99\"");
    }
}
