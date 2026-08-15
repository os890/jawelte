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
package org.os890.jawelte.tests.datasource.scenario11;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * The declaration below is what production would carry: a <b>file</b>
 * database. Run verbatim from a test, it creates that file wherever the
 * JVM happens to run, the file survives the suite, and the next build
 * reopens it with the previous build's rows still in it — which reads
 * as flakiness rather than as leftover state, and leaves a stray file
 * in the working tree.
 *
 * <p>The test run redirects it through MicroProfile Config, keyed by
 * the definition's own name (see this module's
 * {@code microprofile-config.properties}), so nothing about the
 * production declaration has to change and no replacement
 * {@code DataSourceFactory} is needed.
 */
@EnableTestBeans
@DataSourceDefinition(
        name = "java:app/jdbc/LeakyDS",
        className = "org.h2.jdbcx.JdbcDataSource",
        url = "jdbc:h2:./scenario11-should-not-exist",
        user = "sa",
        password = "")
class Scenario11Test {

    @Inject
    DataSource dataSource;

    @Test
    void theConfiguredUrlReplacesTheDeclaredOne() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            // H2 reports the url without the connection settings that
            // followed the ';', so the database identity is what can be
            // asserted on — which is the part the redirect changes.
            assertThat(connection.getMetaData().getURL())
                    .as("the redirect has to reach the vendor object, not just be read")
                    .startsWith("jdbc:h2:mem:scenario11")
                    .doesNotContain("scenario11-should-not-exist");
        }
    }

    @Test
    void noDatabaseFileIsCreatedInTheWorkingTree() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("CREATE TABLE IF NOT EXISTS probe (id INT PRIMARY KEY)");
        }

        assertThat(Files.exists(Path.of("scenario11-should-not-exist.mv.db")))
                .as("a redirected declaration must not leave a database file behind")
                .isFalse();
    }
}
