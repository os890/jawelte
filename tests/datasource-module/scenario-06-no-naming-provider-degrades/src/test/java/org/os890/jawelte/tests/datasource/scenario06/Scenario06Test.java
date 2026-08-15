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
package org.os890.jawelte.tests.datasource.scenario06;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * This scenario's pom deliberately omits {@code xbean-naming}, so
 * there is no JNDI provider in the JVM at all.
 *
 * <p>That has to be survivable rather than fatal. Injection does not
 * go through naming — it goes through the synthetic CDI beans — so a
 * test that never performs a lookup should not care whether a naming
 * provider exists. Binding is therefore skipped, quietly, and the
 * declared data source works exactly as it does everywhere else.
 *
 * <p>A test that <em>does</em> look up gets the naming layer's own
 * error at the point of the lookup, which names the real problem
 * (no {@code InitialContextFactory}) better than any error this module
 * could raise earlier and further away.
 */
@EnableTestBeans
@DataSourceDefinition(
        name = "java:comp/env/jdbc/OrdersDS",
        className = "org.h2.jdbcx.JdbcDataSource",
        url = "jdbc:h2:mem:scenario06;DB_CLOSE_DELAY=-1",
        user = "sa",
        password = "")
class Scenario06Test {

    @Inject
    DataSource dataSource;

    @Test
    void theDeclaredDataSourceIsStillBuiltAndInjected() {
        assertThat(dataSource)
                .as("a missing naming provider must not stop the definition being realised")
                .isNotNull();
    }

    @Test
    void theDeclaredDataSourceStillWorks() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS orders (id INT PRIMARY KEY)");
            statement.execute("MERGE INTO orders KEY(id) VALUES (1)");
        }

        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).contains("scenario06");
        }
    }

    @Test
    void aLookupFailsWithTheNamingLayersOwnError() {
        assertThatThrownBy(() -> new InitialContext().lookup("java:comp/env/jdbc/OrdersDS"))
                .as("the failure has to come from naming, at the point of the lookup — "
                        + "not from the lifecycle adapter at boot")
                .isInstanceOf(NamingException.class);
    }
}
