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
package org.os890.jawelte.tests.dbtestdata.scenario63;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.dbtestdata.api.DbDiff;

class Scenario63Test {

    private Connection connection;

    @BeforeEach
    void openConnection() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:scenario63;MODE=LEGACY;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE CUSTOMER (ID INT PRIMARY KEY, NAME VARCHAR(64))");
            statement.execute("CREATE TABLE SALES_ORDER ("
                    + "ID INT PRIMARY KEY, CUSTOMER_ID INT, NOTE VARCHAR(64))");
            statement.execute("CREATE TABLE ORDER_LINE ("
                    + "ID INT PRIMARY KEY, ORDER_ID INT, ITEM VARCHAR(64))");
            statement.execute("INSERT INTO CUSTOMER VALUES (42, 'Alice')");
            statement.execute("INSERT INTO SALES_ORDER VALUES (100, 42, 'urgent')");
            statement.execute("INSERT INTO ORDER_LINE VALUES (200, 100, 'Widget')");
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
    void labelChainBindsConsistentlyAcrossThreeTables() {
        // Two independent labels @cust1 (CUSTOMER -> SALES_ORDER) and
        // @order1 (SALES_ORDER -> ORDER_LINE) — both bind to actual
        // values and consistency holds across the chain.
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<dataset>"
                + "<CUSTOMER ID=\"@cust1\" NAME=\"Alice\"/>"
                + "<SALES_ORDER ID=\"@order1\" CUSTOMER_ID=\"@cust1\" NOTE=\"urgent\"/>"
                + "<ORDER_LINE ID=\"@line1\" ORDER_ID=\"@order1\" ITEM=\"Widget\"/>"
                + "</dataset>";
        DbDiff.forConnection(connection).expectedContent(expected).assertEquals();
    }
}
