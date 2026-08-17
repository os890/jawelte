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
package org.os890.jawelte.tests.datasource.scenario09;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.annotation.sql.DataSourceDefinitions;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * An application bean — not the test class — injects one of two
 * declared data sources by name. This is the module's headline usage
 * from production code's point of view, and the shape reported in #124
 * as colliding with cdi-module's auto-mock.
 */
@EnableTestBeans
@DataSourceDefinitions({
        @DataSourceDefinition(
                name = "java:app/jdbc/AppDS",
                className = "org.h2.jdbcx.JdbcDataSource",
                url = "jdbc:h2:mem:scenario09_app;DB_CLOSE_DELAY=-1",
                user = "sa", password = ""),
        @DataSourceDefinition(
                name = "java:app/jdbc/MigrationDS",
                className = "org.h2.jdbcx.JdbcDataSource",
                url = "jdbc:h2:mem:scenario09_migration;DB_CLOSE_DELAY=-1",
                user = "sa", password = "")
})
class Scenario09Test {

    @Inject
    MigrationService migrationService;

    @Test
    void anApplicationBeanResolvesTheDataSourceItNamed() throws SQLException {
        assertThat(migrationService).isNotNull();
        assertThat(migrationService.connectedUrl())
                .as("the named injection point must resolve to the declared data source, "
                        + "not to an auto-mock and not ambiguously")
                .contains("scenario09_migration");
    }
}
