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
package org.os890.jawelte.tests.datasource.scenario02;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.annotation.sql.DataSourceDefinitions;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Two declarations, two independent data sources. Each is reachable
 * by its own name — through injection and through JNDI — and they
 * address different databases, which is what makes "independent"
 * observable rather than a claim.
 *
 * <p>With more than one definition none of them is {@code @Default}:
 * an unqualified {@code @Inject DataSource} is genuinely ambiguous,
 * and leaving it unsatisfied says so more clearly than picking a
 * winner would.
 */
@EnableTestBeans
@DataSourceDefinitions({
        @DataSourceDefinition(
                name = "java:comp/env/jdbc/OrdersDS",
                className = "org.h2.jdbcx.JdbcDataSource",
                url = "jdbc:h2:mem:scenario02_orders;DB_CLOSE_DELAY=-1",
                user = "sa",
                password = ""),
        @DataSourceDefinition(
                name = "java:comp/env/jdbc/AuditDS",
                className = "org.h2.jdbcx.JdbcDataSource",
                url = "jdbc:h2:mem:scenario02_audit;DB_CLOSE_DELAY=-1",
                user = "sa",
                password = "")
})
class Scenario02Test {

    @Inject
    @Named("java:comp/env/jdbc/OrdersDS")
    DataSource orders;

    @Inject
    @Named("java:comp/env/jdbc/AuditDS")
    DataSource audit;

    @Test
    void bothDefinitionsAreInjectableByName() {
        assertThat(orders).isNotNull();
        assertThat(audit).isNotNull();
        assertThat(orders).isNotSameAs(audit);
    }

    @Test
    void bothDefinitionsAreBoundUnderTheirOwnJndiName() throws NamingException {
        InitialContext context = new InitialContext();

        assertThat(context.lookup("java:comp/env/jdbc/OrdersDS")).isInstanceOf(DataSource.class);
        assertThat(context.lookup("java:comp/env/jdbc/AuditDS")).isInstanceOf(DataSource.class);
    }

    @Test
    void theTwoDataSourcesAddressSeparateDatabases() throws SQLException {
        createTableWithRow(orders, "orders", "from-orders");
        createTableWithRow(audit, "audit_log", "from-audit");

        assertThat(readLabel(orders, "orders")).isEqualTo("from-orders");
        assertThat(readLabel(audit, "audit_log")).isEqualTo("from-audit");
        assertThat(tableExists(audit, "orders"))
                .as("a table created through one declared data source must not appear in the other")
                .isFalse();
    }

    /**
     * With two declarations, an unqualified {@code DataSource} must not
     * silently resolve to one of them.
     *
     * <p>The two CDI runtimes reach that outcome differently, so the
     * assertion is on the outcome rather than on either shape. Weld
     * applies the spec rule that a bean qualified only with
     * {@code @Named} also carries {@code @Default}, which makes the
     * unqualified lookup <em>ambiguous</em> — two candidates.
     * OpenWebBeans does not, which leaves it <em>unsatisfied</em> —
     * zero candidates. Either way it is not resolvable, which is the
     * property that matters: the caller has to name which data source
     * it means.
     */
    @Test
    void noSingleDataSourceResolvesUnqualifiedWhenThereIsMoreThanOne() {
        Instance<DataSource> unqualified = CDI.current().select(DataSource.class);

        assertThat(unqualified.isResolvable())
                .as("with two declarations an unqualified lookup must not yield one of them — "
                        + "ambiguous (Weld) and unsatisfied (OpenWebBeans) both satisfy that")
                .isFalse();
        assertThat(unqualified.isAmbiguous() || unqualified.isUnsatisfied()).isTrue();
    }

    private static void createTableWithRow(DataSource dataSource, String table, String label) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + table + " (id INT PRIMARY KEY, label VARCHAR(32))");
            statement.execute("MERGE INTO " + table + " KEY(id) VALUES (1, '" + label + "')");
        }
    }

    private static String readLabel(DataSource dataSource, String table) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT label FROM " + table + " WHERE id = 1")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString("label");
        }
    }

    private static boolean tableExists(DataSource dataSource, String table) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                ResultSet tables = connection.getMetaData()
                        .getTables(null, null, table.toUpperCase(java.util.Locale.ROOT), null)) {
            return tables.next();
        }
    }
}
