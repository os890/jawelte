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
package org.os890.jawelte.tests.dbtestdata.scenario34;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.dbtestdata.api.DbDiff;

class Scenario34Test {

    private Connection connection;

    @BeforeEach
    void openConnection() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:scenario34;MODE=LEGACY;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE EVENT (ID INT PRIMARY KEY, V VARCHAR(64))");
            statement.execute("INSERT INTO EVENT VALUES (1, 'x')");
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
    void registeringAnUnknownMethodNameRaisesAtRegistrationTime() {
        // The builder verifies the method exists on declaringClass at
        // registration time; an unknown method name raises before the
        // descriptor is even stored.
        var builder = DbDiff.forConnection(connection)
                .expectedContent(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                + "<dataset>"
                                + "<EVENT ID=\"1\" V=\"${fn:ghost()}\"/>"
                                + "</dataset>");
        assertThatThrownBy(() -> builder.withFunction("fn", "ghost", Functions.class, "thereIsNoSuchMethod"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no public static method")
                .hasMessageContaining("thereIsNoSuchMethod");
    }

    public static class Functions {
        public static String someOtherMethod() {
            return "x";
        }
    }
}
