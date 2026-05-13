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
package org.os890.jawelte.tests.dbtestdata.scenario56;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.dbtestdata.api.DbDiff;
import org.os890.jawelte.module.dbtestdata.api.DbSeed;

class Scenario56Test {

    private Connection connection;

    @BeforeEach
    void openConnection() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:scenario56;MODE=LEGACY;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE AUDIT_LOG (ID BINARY(16) PRIMARY KEY, ACTION VARCHAR(64))");
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
    void uuidMarkerHandlesBinary16ColumnsSymmetricallyOnSeedAndDiff() {
        // The uuid'…' marker is the documented syntax for binary UUID
        // columns: H2DataTypeFactory's UuidAwareBytesDataType parses
        // it on the seed side (typeCast), and MarkerComparator's
        // uuid'…' branch parses it on the diff side and compares
        // against the raw byte[16] returned by JDBC. Same syntax both
        // directions — no implicit byte[] -> string heuristics needed.
        String uuidString = "550e8400-e29b-41d4-a716-446655440000";
        String dataset = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<dataset>"
                + "<AUDIT_LOG ID=\"uuid'" + uuidString + "'\" ACTION=\"CREATE\"/>"
                + "</dataset>";
        DbSeed.forConnection(connection).datasetContent(dataset).execute();
        DbDiff.forConnection(connection).expectedContent(dataset).assertEquals();
    }
}
