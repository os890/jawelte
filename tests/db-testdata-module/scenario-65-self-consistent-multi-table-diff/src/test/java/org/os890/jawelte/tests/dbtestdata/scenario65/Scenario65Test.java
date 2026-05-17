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
package org.os890.jawelte.tests.dbtestdata.scenario65;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.dbtestdata.api.DbDiff;
import org.os890.jawelte.module.dbtestdata.api.DbSeed;

/**
 * Reproducer for the LNP scenario-02 phantom-row diff: when a single
 * XML dataset describes multiple tables with many rows each, seeding
 * the XML and then diffing the live DB against the SAME XML should
 * return zero differences. Any non-zero diff here points at
 * {@code FlatXmlDataSet} column-sensing emitting a phantom row at a
 * table boundary (the symptom was {@code FOO[N]: missing row in
 * database} where the XML genuinely had N rows of FOO).
 */
class Scenario65Test {

    private Connection connection;

    @BeforeEach
    void openConnection() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:scenario65;MODE=LEGACY;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE CUSTOMER (ID INT PRIMARY KEY, NAME VARCHAR(64))");
            statement.execute(
                    "CREATE TABLE PRODUCT (ID INT PRIMARY KEY, SKU VARCHAR(64), PRICE DECIMAL(10,2))");
            statement.execute(
                    "CREATE TABLE ORDER_ITEM (ID INT PRIMARY KEY, QUANTITY INT, UNIT_PRICE DECIMAL(10,2))");
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

    /**
     * Builds a multi-table dataset with 5 CUSTOMER, 10 PRODUCT and 200
     * ORDER_ITEM rows, seeds it through {@link DbSeed}, then diffs
     * the same XML against the just-seeded DB. With the table list in
     * {@code unorderedTables} the diff should be empty regardless of
     * how H2 orders {@code SELECT *} per table.
     */
    @Test
    void seedThenDiffAgainstSameXmlReturnsNoDifferences() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<dataset>");
        for (int i = 1; i <= 5; i++) {
            xml.append("<CUSTOMER ID=\"").append(i)
               .append("\" NAME=\"Customer-").append(i).append("\"/>");
        }
        for (int i = 1; i <= 10; i++) {
            xml.append("<PRODUCT ID=\"").append(i)
               .append("\" SKU=\"SKU-").append(i)
               .append("\" PRICE=\"").append(String.format("%d.00", 10 + i))
               .append("\"/>");
        }
        for (int i = 1; i <= 200; i++) {
            xml.append("<ORDER_ITEM ID=\"").append(i)
               .append("\" QUANTITY=\"").append(i % 5 + 1)
               .append("\" UNIT_PRICE=\"")
               .append(String.format("%d.00", i)).append("\"/>");
        }
        xml.append("</dataset>");

        String dataset = xml.toString();

        DbSeed.forConnection(connection)
                .datasetContent(dataset)
                .cleanInsert()
                .execute();

        DbDiff.forConnection(connection)
                .expectedContent(dataset)
                .unorderedTables("CUSTOMER", "PRODUCT", "ORDER_ITEM")
                .assertEquals();
    }
}
