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
package org.os890.jawelte.tests.resource.scenario02;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** A consumer of the produced data source, so the producer actually runs. */
@ApplicationScoped
public class ReportingService {

    @Inject
    @Reporting
    private DataSource reportingDataSource;

    /** No-arg constructor required by CDI. */
    public ReportingService() {
    }

    /**
     * @return the url the produced data source connects to
     * @throws SQLException if the connection fails
     */
    public String connectedUrl() throws SQLException {
        try (Connection connection = reportingDataSource.getConnection()) {
            return connection.getMetaData().getURL();
        }
    }
}
