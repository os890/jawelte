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
package org.os890.jawelte.tests.datasource.scenario10;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Schema migration, readiness probes and cache warm-up all run while
 * the container starts. A declared data source has to be usable there,
 * because that is exactly the code a test most wants to cover.
 */
@EnableTestBeans
@DataSourceDefinition(
        name = "java:app/jdbc/StartupDS",
        className = "org.h2.jdbcx.JdbcDataSource",
        url = "jdbc:h2:mem:scenario10;DB_CLOSE_DELAY=-1",
        user = "sa", password = "")
class Scenario10Test {

    @Inject
    DataSource dataSource;

    @Test
    void theStartupObserverCouldUseTheDeclaredDataSource() {
        assertThat(SchemaMigration.ran())
                .as("the startup observer has to have been invoked at all")
                .isTrue();
        assertThat(SchemaMigration.failure())
                .as("a data source declared by @DataSourceDefinition must already exist "
                        + "when @Initialized(ApplicationScoped.class) fires")
                .isNull();
    }

    @Test
    void whatTheStartupObserverWroteIsVisibleLater() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM migrated")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isEqualTo(1);
        }
    }
}
