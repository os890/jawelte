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
package org.os890.jawelte.tests.datasource.scenario01;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * A single {@code @DataSourceDefinition} on the test class is enough:
 * no jawelte annotation beyond {@code @EnableTestBeans}, no producer,
 * no setup code. The declared data source has to be usable through
 * all three routes and be the same object in each.
 */
@EnableTestBeans
@DataSourceDefinition(
        name = "java:comp/env/jdbc/OrdersDS",
        className = "org.h2.jdbcx.JdbcDataSource",
        url = "jdbc:h2:mem:scenario01;DB_CLOSE_DELAY=-1",
        user = "sa",
        password = "")
class Scenario01Test {

    /** Sole definition, so it also carries {@code @Default}. */
    @Inject
    DataSource unqualified;

    @Inject
    @Named("java:comp/env/jdbc/OrdersDS")
    DataSource byName;

    @Test
    void theSoleDefinitionSatisfiesAnUnqualifiedInjectionPoint() {
        assertThat(unqualified).isNotNull();
    }

    @Test
    void theDefinitionIsAlsoInjectableByItsDeclaredName() {
        assertThat(byName).isNotNull();
        assertThat(byName).isSameAs(unqualified);
    }

    /**
     * One declaration means one data source, whichever way it is
     * reached. A JDBC {@code DataSource} is almost always
     * {@code Referenceable}, and a naming provider left to its
     * defaults reconstructs such an object on lookup — which would
     * hand a pooled data source's users a second, unrelated pool.
     * The naming provider is configured not to dereference, so the
     * tree hands back what was put into it.
     */
    @Test
    void theDefinitionIsBoundInJndiUnderItsDeclaredName() throws NamingException {
        Object bound = new InitialContext().lookup("java:comp/env/jdbc/OrdersDS");

        assertThat(bound)
                .as("a JNDI lookup must hand back the very object that was injected, "
                        + "not a second one built from the same declaration")
                .isSameAs(unqualified);
    }

    @Test
    void repeatedLookupsResolveToTheSameInstance() throws NamingException {
        InitialContext context = new InitialContext();

        Object first = context.lookup("java:comp/env/jdbc/OrdersDS");
        Object second = context.lookup("java:comp/env/jdbc/OrdersDS");

        assertThat(first).isSameAs(second);
    }

    @Test
    void theDeclaredDataSourceActuallyTalksToTheDatabase() throws SQLException {
        try (Connection connection = unqualified.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS orders (id INT PRIMARY KEY, label VARCHAR(32))");
            statement.execute("MERGE INTO orders KEY(id) VALUES (1, 'first')");

            try (ResultSet resultSet = statement.executeQuery("SELECT label FROM orders WHERE id = 1")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("label")).isEqualTo("first");
            }
        }
    }

    @Test
    void theUrlAttributeReachedTheVendorDataSource() throws SQLException {
        try (Connection connection = unqualified.getConnection()) {
            assertThat(connection.getMetaData().getURL())
                    .as("the url attribute has to be applied to the vendor class through its own setter")
                    .contains("scenario01");
        }
    }
}
