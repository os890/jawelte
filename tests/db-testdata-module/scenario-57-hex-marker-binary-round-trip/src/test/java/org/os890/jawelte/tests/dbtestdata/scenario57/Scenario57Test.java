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
package org.os890.jawelte.tests.dbtestdata.scenario57;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.dbtestdata.api.DbDiff;
import org.os890.jawelte.module.dbtestdata.api.DbSeed;

class Scenario57Test {

    private Connection connection;

    @BeforeEach
    void openConnection() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:scenario57;MODE=LEGACY;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE FILE_HASH (ID INT PRIMARY KEY, SHA1 BINARY(20))");
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
    void hexMarkerHandlesNonUuidBinaryColumnsSymmetricallyOnSeedAndDiff() throws Exception {
        // SHA-1 is 20 bytes — non-UUID-sized BINARY column. The
        // hex'…' marker covers it on both the seed side (text-level
        // hex -> Base64 rewrite before DbUnit parses, then DbUnit's
        // default Base64 typeCast on BytesDataType produces the bytes)
        // and the diff side (MarkerComparator.hexMatches() compares
        // the inner hex against the raw byte[] returned by JDBC).
        // SHA-1 of the empty string (40 hex chars / 20 bytes).
        String hex = "da39a3ee5e6b4b0d3255bfef95601890afd80709";
        String dataset = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<dataset>"
                + "<FILE_HASH ID=\"1\" SHA1=\"hex'" + hex + "'\"/>"
                + "</dataset>";
        DbSeed.forConnection(connection).datasetContent(dataset).execute();

        byte[] expectedBytes = HexFormat.of().parseHex(hex);
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT SHA1 FROM FILE_HASH WHERE ID=1")) {
            assertThat(resultSet.next()).isTrue();
            byte[] actualBytes = resultSet.getBytes(1);
            assertThat(actualBytes).hasSize(20);
            assertThat(actualBytes).isEqualTo(expectedBytes);
        }

        DbDiff.forConnection(connection).expectedContent(dataset).assertEquals();
    }
}
