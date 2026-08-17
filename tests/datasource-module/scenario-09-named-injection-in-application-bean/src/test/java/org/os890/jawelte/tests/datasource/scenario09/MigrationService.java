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

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/** An application bean that names the data source it wants, as production code does. */
@ApplicationScoped
public class MigrationService {

    @Inject
    @Named("java:app/jdbc/MigrationDS")
    DataSource migrationDataSource;

    /** No-arg constructor required by CDI. */
    public MigrationService() {
    }

    /**
     * @return the JDBC url the injected data source actually connects to
     * @throws SQLException if the connection fails
     */
    public String connectedUrl() throws SQLException {
        try (Connection connection = migrationDataSource.getConnection()) {
            return connection.getMetaData().getURL();
        }
    }
}
