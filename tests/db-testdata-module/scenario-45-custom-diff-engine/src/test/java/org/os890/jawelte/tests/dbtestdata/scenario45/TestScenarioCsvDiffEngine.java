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
package org.os890.jawelte.tests.dbtestdata.scenario45;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Priority;

import org.os890.jawelte.module.dbtestdata.api.DbDiff;
import org.os890.jawelte.module.dbtestdata.api.DbDiff.Difference;
import org.os890.jawelte.module.dbtestdata.api.DbDiff.Difference.Kind;
import org.os890.jawelte.module.dbtestdata.api.port.DbDiffEngine;

/**
 * Test-only {@link DbDiffEngine} for the {@code text/csv} format.
 * Parses each non-empty CSV line as {@code ID,NAME} and compares
 * against the {@code CUSTOMER} table in row-order. Produces typed
 * {@link Difference} records — exactly how the bundled engine
 * does — to demonstrate that the api carries the
 * {@link AssertionError} formatting.
 */
@Priority(Integer.MAX_VALUE)
public class TestScenarioCsvDiffEngine implements DbDiffEngine {

    public TestScenarioCsvDiffEngine() {
    }

    @Override
    public String format() {
        return "text/csv";
    }

    @Override
    public List<Difference> diff(Connection connection, String expectedContent, DbDiff.DiffSpec options) {
        List<String[]> expected = new ArrayList<>();
        for (String line : expectedContent.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                expected.add(trimmed.split(","));
            }
        }
        List<Difference> differences = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT ID, NAME FROM CUSTOMER ORDER BY ID")) {
            int rowIndex = 0;
            while (resultSet.next() && rowIndex < expected.size()) {
                String[] row = expected.get(rowIndex);
                if (!resultSet.getString(2).equals(row[1].trim())) {
                    differences.add(new Difference(
                            Kind.VALUE_MISMATCH,
                            "CUSTOMER",
                            rowIndex,
                            "NAME",
                            row[1].trim(),
                            resultSet.getString(2),
                            0));
                }
                rowIndex++;
            }
        } catch (SQLException sqlFailure) {
            throw new RuntimeException(sqlFailure.getMessage(), sqlFailure);
        }
        return List.copyOf(differences);
    }
}
