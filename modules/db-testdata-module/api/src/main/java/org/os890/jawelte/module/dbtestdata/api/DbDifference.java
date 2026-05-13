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
package org.os890.jawelte.module.dbtestdata.api;

import java.util.Objects;

import org.os890.jawelte.module.dbtestdata.api.port.DbDiffEngine;

/**
 * Typed record describing a single mismatch between the expected
 * dataset and the database state. {@link DbDiffEngine} returns one
 * {@link DbDifference} per cell mismatch and one per missing /
 * unexpected row; the api carries the formatting of the resulting
 * {@link AssertionError} message so engines never produce strings.
 *
 * @param kind               the difference category
 * @param tableName          the table the difference is reported
 *                           against
 * @param rowIndex           0-based row index within the table — the
 *                           expected dataset's index for
 *                           {@link DifferenceType#MISSING_ROW} and
 *                           {@link DifferenceType#VALUE_MISMATCH};
 *                           the actual database index for
 *                           {@link DifferenceType#EXTRA_ROW}
 * @param columnName         the column the cell mismatch is reported
 *                           against; {@code null} when {@code kind}
 *                           is not {@link DifferenceType#VALUE_MISMATCH}
 *                           because the difference is row-level
 * @param expected           the expected value as a string — the
 *                           special markers ({@code [NULL]},
 *                           {@code ~regex}, {@code uuid'…'}) are
 *                           carried verbatim so the error message
 *                           shows what the test author wrote
 * @param actual             the database value as a string;
 *                           {@code null} for {@link DifferenceType#MISSING_ROW}
 * @param expectedLineNumber 1-based line number in the expected
 *                           dataset file the difference resolves to;
 *                           {@code 0} when the engine cannot
 *                           determine a meaningful line (e.g.
 *                           inline {@code expectedContent(...)})
 */
public record DbDifference(
        DifferenceType kind,
        String tableName,
        int rowIndex,
        String columnName,
        String expected,
        String actual,
        int expectedLineNumber) {

    /**
     * Canonical constructor. {@code kind} and {@code tableName} are
     * mandatory; {@code columnName} is required when
     * {@code kind == VALUE_MISMATCH} and forbidden otherwise.
     */
    public DbDifference {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(tableName, "tableName");
        if (kind == DifferenceType.VALUE_MISMATCH) {
            Objects.requireNonNull(columnName, "columnName");
        }
    }

    /**
     * Difference category emitted by {@link DbDiffEngine}.
     */
    public enum DifferenceType {

        /** Cell value differs at ({@code tableName}, {@code rowIndex}, {@code columnName}). */
        VALUE_MISMATCH,

        /** A row present in the expected dataset is absent from the database. */
        MISSING_ROW,

        /**
         * A row present in the database is absent from the expected
         * dataset. Only emitted when {@link DbDiff.DiffSpec#subsetOnly()}
         * is {@code false}.
         */
        EXTRA_ROW
    }
}
