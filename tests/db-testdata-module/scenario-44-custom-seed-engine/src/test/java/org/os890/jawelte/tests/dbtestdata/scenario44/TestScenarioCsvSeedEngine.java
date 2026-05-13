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
package org.os890.jawelte.tests.dbtestdata.scenario44;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import jakarta.annotation.Priority;

import org.os890.jawelte.module.dbtestdata.api.SeedSpec;
import org.os890.jawelte.module.dbtestdata.api.port.DbSeedEngine;

/**
 * Test-only {@link DbSeedEngine} for the {@code text/csv} format —
 * each line is parsed as {@code ID,NAME} and inserted into
 * {@code CUSTOMER}. Demonstrates that consumers can ship their own
 * engine via {@link java.util.ServiceLoader} and route to it via
 * {@code DbSeed.Builder.format("text/csv")}.
 */
@Priority(Integer.MAX_VALUE)
public class TestScenarioCsvSeedEngine implements DbSeedEngine {

    public TestScenarioCsvSeedEngine() {
    }

    @Override
    public String format() {
        return "text/csv";
    }

    @Override
    public void seed(Connection connection, String datasetContent, SeedSpec options) {
        try (Statement statement = connection.createStatement()) {
            for (String line : datasetContent.split("\\r?\\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] columns = trimmed.split(",");
                statement.execute("INSERT INTO CUSTOMER VALUES ("
                        + columns[0].trim() + ", '" + columns[1].trim() + "')");
            }
        } catch (SQLException sqlFailure) {
            throw new RuntimeException(sqlFailure.getMessage(), sqlFailure);
        }
    }
}
