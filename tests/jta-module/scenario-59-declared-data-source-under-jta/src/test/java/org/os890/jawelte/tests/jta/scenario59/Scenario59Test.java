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
package org.os890.jawelte.tests.jta.scenario59;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * The failure reported in #123: a persistence unit naming a
 * {@code <jta-data-source>} that a {@code @DataSourceDefinition}
 * declares was still given jpa-module's own generated H2 url, so the
 * declared data source and the persistence unit were two different
 * databases.
 *
 * <p>Asserting the url would be the weak version of this. What an
 * application actually depends on is that a row written through one is
 * readable through the other - a schema migrated with plain JDBC and
 * then read through JPA - so both directions are asserted, and the
 * schema JPA generated is what the JDBC half writes into.
 */
@EnableTestBeans
class Scenario59Test {

    @Inject
    NoteService noteService;

    @Inject
    DataSource dataSource;

    @Test
    void aRowWrittenThroughTheDataSourceIsVisibleThroughJpa() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement insert =
                        connection.prepareStatement("INSERT INTO NOTE (ID, TEXT) VALUES (?, ?)")) {
            insert.setLong(1, 1L);
            insert.setString(2, "written-through-jdbc");
            insert.executeUpdate();
        }

        assertThat(noteService.read(1L))
                .as("the persistence unit has to be on the database its <jta-data-source> names")
                .isEqualTo("written-through-jdbc");
    }

    @Test
    void aRowWrittenThroughJpaIsVisibleThroughTheDataSource() throws SQLException {
        noteService.write(2L, "written-through-jpa");

        try (Connection connection = dataSource.getConnection();
                Statement select = connection.createStatement();
                ResultSet rows = select.executeQuery("SELECT TEXT FROM NOTE WHERE ID = 2")) {
            assertThat(rows.next())
                    .as("a row JPA wrote must be reachable through the declared data source")
                    .isTrue();
            assertThat(rows.getString(1)).isEqualTo("written-through-jpa");
        }
    }

    @Test
    void theSchemaJpaGeneratedLivesInTheDeclaredDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL())
                    .as("one database, named by the declaration rather than generated")
                    .contains("scenario59");
        }
    }
}
