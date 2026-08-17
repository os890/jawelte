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
package org.os890.jawelte.tests.datasource.scenario04;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * An application bean that declares the data source it needs, the way
 * production code does — the declaration sits on the component, not on
 * the test.
 */
@ApplicationScoped
@DataSourceDefinition(
        name = "java:app/jdbc/InventoryDS",
        className = "org.h2.jdbcx.JdbcDataSource",
        url = "jdbc:h2:mem:scenario04;DB_CLOSE_DELAY=-1",
        user = "sa",
        password = "")
public class InventoryRepository {

    @Inject
    DataSource dataSource;

    /** No-arg constructor required by CDI. */
    public InventoryRepository() {
    }

    /**
     * Create the table if needed and store one item.
     *
     * @param id   the item id
     * @param name the item name
     * @throws SQLException if the statement fails
     */
    public void store(int id, String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS inventory (id INT PRIMARY KEY, name VARCHAR(32))");
            statement.execute("MERGE INTO inventory KEY(id) VALUES (" + id + ", '" + name + "')");
        }
    }

    /**
     * Read an item's name back.
     *
     * @param id the item id
     * @return the stored name, or {@code null} if there is no such row
     * @throws SQLException if the query fails
     */
    public String read(int id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT name FROM inventory WHERE id = " + id)) {
            return resultSet.next() ? resultSet.getString("name") : null;
        }
    }
}
