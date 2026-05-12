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
package org.os890.jawelte.tests.dbtestdata.scenario39;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.dbtestdata.api.DbSeed;

class Scenario39Test {

    private Connection seedConnection;

    private Connection verifyConnection;

    @BeforeEach
    void openConnections() throws Exception {
        seedConnection = DriverManager.getConnection(
                "jdbc:h2:mem:scenario39;MODE=LEGACY;DB_CLOSE_DELAY=-1", "sa", "");
        seedConnection.setAutoCommit(true);
        verifyConnection = DriverManager.getConnection(
                "jdbc:h2:mem:scenario39;MODE=LEGACY;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement statement = seedConnection.createStatement()) {
            statement.execute("CREATE TABLE CUSTOMER (ID INT PRIMARY KEY, NAME VARCHAR(64))");
        }
    }

    @AfterEach
    void closeConnections() throws Exception {
        if (verifyConnection != null && !verifyConnection.isClosed()) {
            try (Statement statement = verifyConnection.createStatement()) {
                statement.execute("DROP ALL OBJECTS");
            }
            verifyConnection.close();
        }
        if (seedConnection != null && !seedConnection.isClosed()) {
            seedConnection.close();
        }
    }

    @Test
    void seedRowsAreVisibleToASeparateConnectionWhenAutoCommitIsOn() throws Exception {
        String dataset = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<dataset>"
                + "<CUSTOMER ID=\"1\" NAME=\"Alice\"/>"
                + "</dataset>";
        DbSeed.forConnection(seedConnection)
                .datasetContent(dataset)
                .cleanInsert()
                .execute();
        try (Statement statement = verifyConnection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT NAME FROM CUSTOMER WHERE ID = 1")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString(1)).isEqualTo("Alice");
        }
    }
}
