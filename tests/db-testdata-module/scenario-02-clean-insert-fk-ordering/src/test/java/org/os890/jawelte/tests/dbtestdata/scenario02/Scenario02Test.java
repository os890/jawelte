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
package org.os890.jawelte.tests.dbtestdata.scenario02;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.dbtestdata.api.DbSeed;

class Scenario02Test {

    private Connection connection;

    @BeforeEach
    void openConnection() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:scenario02;MODE=LEGACY;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE CUSTOMER (ID INT PRIMARY KEY, NAME VARCHAR(64))");
            statement.execute("CREATE TABLE \"SALES_ORDER\" ("
                    + "ID INT PRIMARY KEY, "
                    + "CUSTOMER_ID INT NOT NULL, "
                    + "FOREIGN KEY (CUSTOMER_ID) REFERENCES CUSTOMER(ID))");
            // Pre-existing rows so cleanInsert must DELETE before INSERT.
            statement.execute("INSERT INTO CUSTOMER VALUES (99, 'pre-existing')");
            statement.execute("INSERT INTO \"SALES_ORDER\" VALUES (999, 99)");
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
    void cleanInsertHonorsForeignKeyDependencyOrder() throws Exception {
        // CleanInsert must DELETE the child table (SALES_ORDER) before the
        // parent (CUSTOMER) — DbUnit's reverse-FK ordering is what
        // makes this succeed under the FK constraint.
        String dataset = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<dataset>"
                + "<CUSTOMER ID=\"1\" NAME=\"Alice\"/>"
                + "<SALES_ORDER ID=\"100\" CUSTOMER_ID=\"1\"/>"
                + "</dataset>";

        DbSeed.forConnection(connection)
                .datasetContent(dataset)
                .cleanInsert()
                .execute();

        try (Statement statement = connection.createStatement();
                ResultSet customers = statement.executeQuery("SELECT COUNT(*) FROM CUSTOMER")) {
            customers.next();
            assertThat(customers.getInt(1)).isEqualTo(1);
        }
        try (Statement statement = connection.createStatement();
                ResultSet orders = statement.executeQuery(
                        "SELECT CUSTOMER_ID FROM \"SALES_ORDER\"")) {
            orders.next();
            assertThat(orders.getInt(1)).isEqualTo(1);
        }
    }
}
