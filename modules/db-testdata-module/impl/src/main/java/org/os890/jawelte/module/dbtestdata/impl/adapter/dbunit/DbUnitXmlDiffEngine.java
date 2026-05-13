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
package org.os890.jawelte.module.dbtestdata.impl.adapter.dbunit;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.annotation.Priority;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableIterator;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.os890.jawelte.core.api.port.ServicePriorityResolver;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.dbtestdata.api.DbDifference;
import org.os890.jawelte.module.dbtestdata.api.DbDifference.DifferenceType;
import org.os890.jawelte.module.dbtestdata.api.DiffSpec;
import org.os890.jawelte.module.dbtestdata.api.InterpolationContext;
import org.os890.jawelte.module.dbtestdata.api.port.DbDiffEngine;
import org.os890.jawelte.module.dbtestdata.api.port.ELInterpolator;
import org.os890.jawelte.module.dbtestdata.impl.util.CellPredicateEvaluator;
import org.os890.jawelte.module.dbtestdata.impl.util.ExpectedXmlLineLocator;
import org.os890.jawelte.module.dbtestdata.impl.util.IgnorePatternMatcher;
import org.os890.jawelte.module.dbtestdata.impl.util.MarkerComparator;

/**
 * Default {@link DbDiffEngine} for the {@code "dbunit-xml"} format.
 *
 * <p>Two parser passes over the expected dataset: the first via
 * DbUnit's {@link FlatXmlDataSetBuilder} produces a structured
 * {@link IDataSet} the engine walks for table and column metadata,
 * and the second via {@link ExpectedXmlLineLocator} captures the
 * 1-based line number of every row element so the resulting
 * {@link DbDifference} records point the test author at the failing
 * fixture line.
 *
 * <p>Per expected table the engine issues a single
 * {@code SELECT * FROM <table>}, materialises the actual rows into
 * a {@code List<Map<column, value>>}, and compares each expected
 * row against an actual row. Cell comparison goes through
 * {@link MarkerComparator}; column-level ignore patterns are
 * filtered out via {@link IgnorePatternMatcher}.
 *
 * <p>Two row-matching modes are supported:
 *
 * <ul>
 *   <li>ordered (default) — expected row {@code i} is compared
 *       against actual row {@code i};</li>
 *   <li>unordered (when the table is listed in
 *       {@link DiffSpec#unorderedTables()}) — multiset matching with
 *       a claim flag per actual row.</li>
 * </ul>
 *
 * <p>Stateless and thread-safe; ships at
 * {@code @Priority(Integer.MAX_VALUE)}.
 */
@Priority(Integer.MAX_VALUE)
public class DbUnitXmlDiffEngine implements DbDiffEngine {

    /** Format identifier this engine claims. */
    public static final String FORMAT = "dbunit-xml";

    /**
     * Recognises {@code @label} back-references on the diff side. A
     * cell whose entire expected value matches {@code @<identifier>}
     * binds the label to whatever actual value the database returned;
     * every other cell sharing the same label must surface the same
     * actual or a {@link DifferenceType#VALUE_MISMATCH} is emitted.
     * Identifier characters are restricted to {@code [A-Za-z0-9_]} so
     * arbitrary VARCHAR content starting with {@code @} (e-mail-like
     * strings) is not silently captured as a binding.
     */
    private static final Pattern LABEL_PATTERN = Pattern.compile("@[A-Za-z0-9_]+");

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public DbUnitXmlDiffEngine() {
    }

    @Override
    public String format() {
        return FORMAT;
    }

    @Override
    public List<DbDifference> diff(Connection connection, String expectedContent, DiffSpec options) {
        IDataSet expectedDataset = parseExpected(expectedContent);
        ExpectedXmlLineLocator lineLocator = ExpectedXmlLineLocator.parse(expectedContent);
        InterpolationContext interpolationContext = options.interpolationContext();
        ELInterpolator interpolator = resolveInterpolator();
        CellPredicateEvaluator predicateEvaluator =
                (expression, actualValue) ->
                        interpolator.evaluatePredicate(expression, interpolationContext, actualValue);
        Map<String, List<RecordedBinding>> labelBindings = new LinkedHashMap<>();
        DiffContext context = new DiffContext(
                new MarkerComparator(
                        options.booleanTrueValues(),
                        options.booleanFalseValues(),
                        predicateEvaluator),
                new IgnorePatternMatcher(options.ignorePatterns()),
                lineLocator,
                upperCaseSet(options.unorderedTables()),
                options.subsetOnly(),
                labelBindings);

        List<DbDifference> differences = new ArrayList<>();
        Set<String> tablesHandledByDbunit = new HashSet<>();
        try {
            ITableIterator iterator = expectedDataset.iterator();
            while (iterator.next()) {
                ITable expectedTable = iterator.getTable();
                String tableName = expectedTable.getTableMetaData().getTableName();
                tablesHandledByDbunit.add(tableName.toUpperCase(Locale.ROOT));
                List<Map<String, Object>> actualRows = readTable(connection, tableName);
                TableScope scope = new TableScope(tableName, expectedTable,
                        expectedTable.getTableMetaData().getColumns(), actualRows);
                if (context.unorderedTables().contains(tableName.toUpperCase(Locale.ROOT))) {
                    compareUnordered(differences, scope, context);
                } else {
                    compareOrdered(differences, scope, context);
                }
            }
            for (String emptyTable : lineLocator.emptyTableNames()) {
                if (tablesHandledByDbunit.contains(emptyTable)) {
                    continue;
                }
                checkEmptyTableAssertion(differences, connection, emptyTable, context.subsetOnly());
            }
            validateLabelBindings(differences, labelBindings);
        } catch (SQLException sqlFailure) {
            throw new RuntimeException(sqlFailure.getMessage(), sqlFailure);
        } catch (Exception dbunitFailure) {
            throw new RuntimeException(dbunitFailure.getMessage(), dbunitFailure);
        }
        return List.copyOf(differences);
    }

    private static String extractLabel(String expected) {
        if (expected == null) {
            return null;
        }
        if (!LABEL_PATTERN.matcher(expected).matches()) {
            return null;
        }
        return expected.substring(1);
    }

    private static void validateLabelBindings(
            List<DbDifference> differences, Map<String, List<RecordedBinding>> labelBindings) {
        for (Map.Entry<String, List<RecordedBinding>> entry : labelBindings.entrySet()) {
            String label = entry.getKey();
            List<RecordedBinding> bindings = entry.getValue();
            if (bindings.size() < 2) {
                continue;
            }
            String canonical = bindings.get(0).actualValue();
            for (int i = 1; i < bindings.size(); i++) {
                RecordedBinding binding = bindings.get(i);
                if (!Objects.equals(canonical, binding.actualValue())) {
                    differences.add(new DbDifference(
                            DifferenceType.VALUE_MISMATCH,
                            binding.tableName(),
                            binding.rowIndex(),
                            binding.columnName(),
                            "@" + label + " bound to \"" + canonical + "\"",
                            binding.actualValue(),
                            binding.lineNumber()));
                }
            }
        }
    }

    private static void checkEmptyTableAssertion(
            List<DbDifference> differences,
            Connection connection,
            String emptyTable,
            boolean subsetOnly) throws SQLException {
        if (subsetOnly) {
            return;
        }
        List<Map<String, Object>> actualRows = readTable(connection, emptyTable);
        for (int rowIndex = 0; rowIndex < actualRows.size(); rowIndex++) {
            differences.add(new DbDifference(
                    DifferenceType.EXTRA_ROW,
                    emptyTable,
                    rowIndex,
                    null,
                    null,
                    actualRowSnapshot(actualRows.get(rowIndex)),
                    0));
        }
    }

    private static Set<String> upperCaseSet(List<String> source) {
        Set<String> result = new HashSet<>();
        for (String entry : source) {
            result.add(entry.toUpperCase(Locale.ROOT));
        }
        return result;
    }

    private static volatile ELInterpolator cachedInterpolator;

    private static ELInterpolator resolveInterpolator() {
        ELInterpolator local = cachedInterpolator;
        if (local != null) {
            return local;
        }
        synchronized (DbUnitXmlDiffEngine.class) {
            local = cachedInterpolator;
            if (local != null) {
                return local;
            }
            List<ELInterpolator> matching = new ArrayList<>();
            for (ELInterpolator candidate : ServiceLoader.load(ELInterpolator.class)) {
                matching.add(candidate);
            }
            if (matching.isEmpty()) {
                throw new IllegalStateException(
                        "No ELInterpolator registered — was db-testdata-module/impl included?");
            }
            ServicePriorityResolver resolver = TestContext.loadService(ServicePriorityResolver.class);
            local = resolver.resolve(matching);
            cachedInterpolator = local;
            return local;
        }
    }

    private static IDataSet parseExpected(String expectedContent) {
        try {
            FlatXmlDataSetBuilder builder = new FlatXmlDataSetBuilder();
            builder.setColumnSensing(true);
            return builder.build(new StringReader(expectedContent));
        } catch (Exception parseFailure) {
            throw new IllegalArgumentException(
                    "Malformed dataset: " + parseFailure.getMessage(), parseFailure);
        }
    }

    private static List<Map<String, Object>> readTable(Connection connection, String tableName)
            throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT * FROM " + tableName)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int columnIndex = 1; columnIndex <= metaData.getColumnCount(); columnIndex++) {
                    String columnName = metaData.getColumnLabel(columnIndex).toUpperCase(Locale.ROOT);
                    row.put(columnName, resultSet.getObject(columnIndex));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private static void compareOrdered(
            List<DbDifference> differences, TableScope scope, DiffContext context) throws Exception {
        int expectedRowCount = scope.expectedTable().getRowCount();
        for (int rowIndex = 0; rowIndex < expectedRowCount; rowIndex++) {
            int lineNumber = context.lineLocator().lineFor(scope.tableName(), rowIndex);
            if (rowIndex >= scope.actualRows().size()) {
                differences.add(new DbDifference(
                        DifferenceType.MISSING_ROW,
                        scope.tableName(),
                        rowIndex,
                        null,
                        rowSnapshot(scope.expectedTable(), rowIndex, scope.columns()),
                        null,
                        lineNumber));
                continue;
            }
            compareRow(differences, scope, context, rowIndex, lineNumber);
        }
        if (!context.subsetOnly() && scope.actualRows().size() > expectedRowCount) {
            for (int extraRowIndex = expectedRowCount; extraRowIndex < scope.actualRows().size(); extraRowIndex++) {
                differences.add(new DbDifference(
                        DifferenceType.EXTRA_ROW,
                        scope.tableName(),
                        extraRowIndex,
                        null,
                        null,
                        actualRowSnapshot(scope.actualRows().get(extraRowIndex)),
                        0));
            }
        }
    }

    private static void compareUnordered(
            List<DbDifference> differences, TableScope scope, DiffContext context) throws Exception {
        int expectedRowCount = scope.expectedTable().getRowCount();
        boolean[] actualClaimed = new boolean[scope.actualRows().size()];
        for (int rowIndex = 0; rowIndex < expectedRowCount; rowIndex++) {
            int claimedIndex = findMatchingActualRow(scope, context, rowIndex, actualClaimed);
            if (claimedIndex < 0) {
                differences.add(new DbDifference(
                        DifferenceType.MISSING_ROW,
                        scope.tableName(),
                        rowIndex,
                        null,
                        rowSnapshot(scope.expectedTable(), rowIndex, scope.columns()),
                        null,
                        context.lineLocator().lineFor(scope.tableName(), rowIndex)));
            } else {
                actualClaimed[claimedIndex] = true;
                recordLabelBindingsForRow(
                        scope, context, rowIndex, claimedIndex,
                        context.lineLocator().lineFor(scope.tableName(), rowIndex));
            }
        }
        if (context.subsetOnly()) {
            return;
        }
        for (int actualIndex = 0; actualIndex < scope.actualRows().size(); actualIndex++) {
            if (!actualClaimed[actualIndex]) {
                differences.add(new DbDifference(
                        DifferenceType.EXTRA_ROW,
                        scope.tableName(),
                        actualIndex,
                        null,
                        null,
                        actualRowSnapshot(scope.actualRows().get(actualIndex)),
                        0));
            }
        }
    }

    private static int findMatchingActualRow(
            TableScope scope, DiffContext context, int expectedRowIndex, boolean[] actualClaimed)
            throws Exception {
        outer:
        for (int actualIndex = 0; actualIndex < scope.actualRows().size(); actualIndex++) {
            if (actualClaimed[actualIndex]) {
                continue;
            }
            Map<String, Object> actualRow = scope.actualRows().get(actualIndex);
            for (Column column : scope.columns()) {
                String columnName = column.getColumnName();
                if (context.ignoreMatcher().isIgnored(scope.tableName(), columnName)) {
                    continue;
                }
                Object expectedRaw = scope.expectedTable().getValue(expectedRowIndex, columnName);
                String expectedString = expectedRaw == null ? null : expectedRaw.toString();
                if (extractLabel(expectedString) != null) {
                    // Label cells are wildcards during row matching;
                    // the cross-cell value-consistency check happens
                    // after the row is claimed (see compareRow).
                    continue;
                }
                Object actualValue = actualRow.get(columnName.toUpperCase(Locale.ROOT));
                if (expectedString == null) {
                    if (actualValue != null) {
                        continue outer;
                    }
                } else if (!context.comparator().matches(expectedString, actualValue)) {
                    continue outer;
                }
            }
            return actualIndex;
        }
        return -1;
    }

    private static void compareRow(
            List<DbDifference> differences,
            TableScope scope,
            DiffContext context,
            int rowIndex,
            int lineNumber) throws Exception {
        Map<String, Object> actualRow = scope.actualRows().get(rowIndex);
        for (Column column : scope.columns()) {
            String columnName = column.getColumnName();
            if (context.ignoreMatcher().isIgnored(scope.tableName(), columnName)) {
                continue;
            }
            Object expectedRaw = scope.expectedTable().getValue(rowIndex, columnName);
            String expectedString = expectedRaw == null ? null : expectedRaw.toString();
            Object actualValue = actualRow.get(columnName.toUpperCase(Locale.ROOT));
            String label = extractLabel(expectedString);
            if (label != null) {
                recordLabelBinding(context, label, scope.tableName(), rowIndex, columnName,
                        lineNumber, actualValue);
                continue;
            }
            boolean cellMatches = expectedString == null
                    ? actualValue == null
                    : context.comparator().matches(expectedString, actualValue);
            if (!cellMatches) {
                differences.add(new DbDifference(
                        DifferenceType.VALUE_MISMATCH,
                        scope.tableName(),
                        rowIndex,
                        columnName,
                        expectedString == null ? "[NULL]" : expectedString,
                        actualValue == null ? "[NULL]" : actualValue.toString(),
                        lineNumber));
            }
        }
    }

    private static void recordLabelBindingsForRow(
            TableScope scope, DiffContext context, int expectedRowIndex, int actualRowIndex,
            int lineNumber) throws Exception {
        Map<String, Object> actualRow = scope.actualRows().get(actualRowIndex);
        for (Column column : scope.columns()) {
            String columnName = column.getColumnName();
            if (context.ignoreMatcher().isIgnored(scope.tableName(), columnName)) {
                continue;
            }
            Object expectedRaw = scope.expectedTable().getValue(expectedRowIndex, columnName);
            String expectedString = expectedRaw == null ? null : expectedRaw.toString();
            String label = extractLabel(expectedString);
            if (label == null) {
                continue;
            }
            Object actualValue = actualRow.get(columnName.toUpperCase(Locale.ROOT));
            recordLabelBinding(context, label, scope.tableName(), expectedRowIndex, columnName,
                    lineNumber, actualValue);
        }
    }

    private static void recordLabelBinding(
            DiffContext context, String label, String tableName, int rowIndex,
            String columnName, int lineNumber, Object actualValue) {
        context.labelBindings()
                .computeIfAbsent(label, key -> new ArrayList<>())
                .add(new RecordedBinding(tableName, rowIndex, columnName, lineNumber,
                        actualValue == null ? "[NULL]" : actualValue.toString()));
    }

    private static String rowSnapshot(ITable expectedTable, int rowIndex, Column[] columns) throws Exception {
        StringBuilder snapshot = new StringBuilder("{");
        for (int columnIndex = 0; columnIndex < columns.length; columnIndex++) {
            if (columnIndex > 0) {
                snapshot.append(", ");
            }
            String columnName = columns[columnIndex].getColumnName();
            Object value = expectedTable.getValue(rowIndex, columnName);
            snapshot.append(columnName).append('=').append(value == null ? "[NULL]" : value);
        }
        snapshot.append('}');
        return snapshot.toString();
    }

    private static String actualRowSnapshot(Map<String, Object> actualRow) {
        StringBuilder snapshot = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : actualRow.entrySet()) {
            if (!first) {
                snapshot.append(", ");
            }
            snapshot.append(entry.getKey()).append('=')
                    .append(entry.getValue() == null ? "[NULL]" : entry.getValue());
            first = false;
        }
        snapshot.append('}');
        return snapshot.toString();
    }

    /**
     * Per-call comparison settings shared across every table.
     * Carries the comparator, ignore matcher, line locator, the set
     * of unordered tables (upper-cased), the subset-only flag, and
     * the {@code @label} binding accumulator. The map's reference
     * is final; the engine appends entries to its lists across the
     * row comparison passes and reads them back when validating
     * cross-cell label consistency.
     */
    private record DiffContext(
            MarkerComparator comparator,
            IgnorePatternMatcher ignoreMatcher,
            ExpectedXmlLineLocator lineLocator,
            Set<String> unorderedTables,
            boolean subsetOnly,
            Map<String, List<RecordedBinding>> labelBindings) { }

    /**
     * Per-table scope shared by the helpers — table name, expected
     * {@link ITable} + its columns, and the materialised actual rows
     * read from the database.
     */
    private record TableScope(
            String tableName,
            ITable expectedTable,
            Column[] columns,
            List<Map<String, Object>> actualRows) { }

    /**
     * Captures the actual database value seen at a specific
     * {@code @label} occurrence so the engine can later check every
     * cell sharing the label agrees on the same actual value.
     *
     * @param tableName    the expected table where the label appears
     * @param rowIndex     the 0-based expected row index
     * @param columnName   the column whose expected value is
     *                     {@code @<label>}
     * @param lineNumber   the 1-based line in the expected dataset
     *                     where the row sits (for the
     *                     {@code DbDifference.expectedLineNumber} field)
     * @param actualValue  the database value as a string;
     *                     {@code "[NULL]"} when the cell is SQL NULL
     */
    private record RecordedBinding(
            String tableName,
            int rowIndex,
            String columnName,
            int lineNumber,
            String actualValue) { }
}
