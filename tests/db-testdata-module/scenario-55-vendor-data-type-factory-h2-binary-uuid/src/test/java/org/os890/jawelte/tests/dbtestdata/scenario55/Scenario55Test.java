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
package org.os890.jawelte.tests.dbtestdata.scenario55;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.dbtestdata.api.DbSeed;

class Scenario55Test {

    private Connection connection;

    @BeforeEach
    void openConnection() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:scenario55;MODE=LEGACY;DB_CLOSE_DELAY=-1", "sa", "");
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
    void uuidLiteralSeedsIntoBinary16ColumnViaVendorFactory() throws Exception {
        // H2's BINARY(16) column needs DbUnit's H2DataTypeFactory
        // (its UuidAwareBytesDataType) to accept the uuid'…' marker
        // — the default factory treats BINARY columns as hex-encoded
        // strings and rejects the marker.
        String uuidString = "550e8400-e29b-41d4-a716-446655440000";
        String dataset = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<dataset>"
                + "<AUDIT_LOG ID=\"uuid'" + uuidString + "'\" ACTION=\"CREATE\"/>"
                + "</dataset>";
        DbSeed.forConnection(connection).datasetContent(dataset).execute();

        UUID expected = UUID.fromString(uuidString);
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(expected.getMostSignificantBits());
        buffer.putLong(expected.getLeastSignificantBits());
        byte[] expectedBytes = buffer.array();

        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT ID, ACTION FROM AUDIT_LOG")) {
            assertThat(resultSet.next()).isTrue();
            byte[] actualBytes = resultSet.getBytes(1);
            assertThat(actualBytes).hasSize(16);
            assertThat(actualBytes).isEqualTo(expectedBytes);
            assertThat(resultSet.getString(2)).isEqualTo("CREATE");
            assertThat(resultSet.next()).isFalse();
        }
    }
}
