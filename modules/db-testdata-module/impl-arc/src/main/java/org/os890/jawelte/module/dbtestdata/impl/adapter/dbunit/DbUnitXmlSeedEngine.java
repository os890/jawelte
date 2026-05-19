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
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.Priority;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ReplacementDataSet;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.os890.jawelte.module.dbtestdata.api.DbSeed.SeedSpec;
import org.os890.jawelte.module.dbtestdata.api.DbSeed.SeedSpec.SeedMode;
import org.os890.jawelte.module.dbtestdata.api.port.DbSeedEngine;
import org.os890.jawelte.module.dbtestdata.impl.util.DataTypeFactoryResolver;

/**
 * Default {@link DbSeedEngine} for the {@code "dbunit-xml"} format.
 *
 * <p>Wraps the caller's JDBC connection in a DbUnit
 * {@link DatabaseConnection}, parses the dataset content through
 * {@link FlatXmlDataSetBuilder} into an {@link IDataSet}, wraps the
 * dataset in a {@link ReplacementDataSet} that maps the
 * case-sensitive {@code [NULL]} marker to a real SQL {@code NULL},
 * and executes the {@link DatabaseOperation} that corresponds to the
 * builder's {@link SeedMode}.
 *
 * <p>The connection is never closed, committed, or rolled back by
 * this engine. The DbUnit wrapper does not take ownership of the
 * JDBC connection in this path — it is allowed to fall out of scope
 * for garbage collection after {@link #seed(Connection, String, SeedSpec)}
 * returns.
 *
 * <p>Stateless and thread-safe; ships at
 * {@code @Priority(Integer.MAX_VALUE)}.
 */
@Priority(Integer.MAX_VALUE)
public class DbUnitXmlSeedEngine implements DbSeedEngine {

    /** Format identifier this engine claims. */
    public static final String FORMAT = "dbunit-xml";

    private static final Pattern HEX_MARKER_PATTERN = Pattern.compile("hex'([0-9a-fA-F]+)'");

    private static final Map<SeedMode, DatabaseOperation> MODE_TO_OPERATION = new EnumMap<>(SeedMode.class);

    static {
        MODE_TO_OPERATION.put(SeedMode.CLEAN_INSERT, DatabaseOperation.CLEAN_INSERT);
        MODE_TO_OPERATION.put(SeedMode.INSERT, DatabaseOperation.INSERT);
        MODE_TO_OPERATION.put(SeedMode.UPDATE, DatabaseOperation.UPDATE);
        MODE_TO_OPERATION.put(SeedMode.REFRESH, DatabaseOperation.REFRESH);
    }

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public DbUnitXmlSeedEngine() {
    }

    @Override
    public String format() {
        return FORMAT;
    }

    @Override
    public void seed(Connection connection, String datasetContent, SeedSpec options) {
        String preprocessed = rewriteHexMarkersAsBase64(datasetContent);
        IDataSet dataset;
        try {
            FlatXmlDataSetBuilder builder = new FlatXmlDataSetBuilder();
            builder.setColumnSensing(true);
            IDataSet flatDataset = builder.build(new StringReader(preprocessed));
            ReplacementDataSet replacements = new ReplacementDataSet(flatDataset);
            replacements.addReplacementObject("[NULL]", null);
            dataset = replacements;
        } catch (Exception parseFailure) {
            throw new IllegalArgumentException(
                    "Malformed dataset: " + parseFailure.getMessage(), parseFailure);
        }
        DatabaseOperation operation = MODE_TO_OPERATION.get(options.mode());
        try {
            IDatabaseConnection dbunitConnection = new DatabaseConnection(connection);
            applyVendorDataTypeFactory(dbunitConnection, connection);
            operation.execute(dbunitConnection, dataset);
            advanceIdentityCountersPastSeededIds(connection, dataset);
        } catch (Exception executionFailure) {
            throw new RuntimeException(executionFailure.getMessage(), executionFailure);
        }
    }

    /**
     * After DbUnit inserts rows with explicit IDs, advance each
     * auto-increment / IDENTITY column's counter so the next
     * caller-issued INSERT (typically via JPA) gets a value past the
     * highest seeded row.
     *
     * <p>Without this step, H2 in its default (non-LEGACY) mode keeps
     * the IDENTITY counter at its initial value when explicit IDs are
     * inserted; the very next {@code INSERT} that omits the ID column
     * then auto-assigns {@code 1}, collides with the row seeded under
     * {@code ID=1}, and the operation fails with a primary-key
     * violation. This is the LNP scenario-02
     * {@code addItemToOrder} failure shape; see scenario-66 for the
     * isolated reproducer.
     *
     * <p>Currently emits the H2-specific {@code ALTER TABLE ... ALTER
     * COLUMN ... RESTART WITH} statement; other vendors are detected
     * by {@link DatabaseMetaData#getDatabaseProductName()} and skipped
     * with no error (their IDENTITY counters typically auto-advance
     * on explicit INSERT, which is why the LNP-style pattern works on
     * them out of the box). Adding a vendor adapter follows the same
     * port shape as {@link DataTypeFactoryResolver}.
     *
     * @param connection the JDBC connection just used for the seed
     * @param dataset    the DbUnit dataset that was inserted - its
     *                   table names determine which counters need
     *                   advancing
     * @throws SQLException if introspection or the RESTART statement
     *                      itself fails
     */
    private static void advanceIdentityCountersPastSeededIds(
            Connection connection, IDataSet dataset) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        String productName = metaData.getDatabaseProductName();
        if (!"H2".equalsIgnoreCase(productName)) {
            return;
        }
        String[] tableNames = dataset.getTableNames();
        for (String tableName : tableNames) {
            for (String idColumn : findAutoIncrementColumns(metaData, tableName)) {
                Long maxValue = readMaxValue(connection, tableName, idColumn);
                if (maxValue == null) {
                    continue;
                }
                restartIdentity(connection, tableName, idColumn, maxValue + 1L);
            }
        }
    }

    private static java.util.List<String> findAutoIncrementColumns(
            DatabaseMetaData metaData, String tableName) throws SQLException {
        java.util.List<String> columns = new java.util.ArrayList<>();
        try (ResultSet rs = metaData.getColumns(null, null, tableName, null)) {
            while (rs.next()) {
                String isAutoIncrement = rs.getString("IS_AUTOINCREMENT");
                if ("YES".equalsIgnoreCase(isAutoIncrement)) {
                    columns.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        return columns;
    }

    private static Long readMaxValue(
            Connection connection, String table, String column) throws SQLException {
        String quotedTable = quoteIdentifier(table);
        String quotedColumn = quoteIdentifier(column);
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT MAX(" + quotedColumn + ") FROM " + quotedTable)) {
            if (!result.next()) {
                return null;
            }
            long max = result.getLong(1);
            return result.wasNull() ? null : max;
        }
    }

    private static void restartIdentity(
            Connection connection, String table, String column, long nextValue)
            throws SQLException {
        String alter = "ALTER TABLE " + quoteIdentifier(table)
                + " ALTER COLUMN " + quoteIdentifier(column)
                + " RESTART WITH " + nextValue;
        try (Statement statement = connection.createStatement()) {
            statement.execute(alter);
        }
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"").toUpperCase(Locale.ROOT) + "\"";
    }

    private static void applyVendorDataTypeFactory(
            IDatabaseConnection dbunitConnection, Connection connection) {
        IDataTypeFactory factory = DataTypeFactoryResolver.resolveFactory(connection);
        if (factory != null) {
            dbunitConnection.getConfig()
                    .setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, factory);
        }
    }

    /**
     * Rewrites every {@code hex'…'} marker in the dataset content with
     * the Base64-encoded form of the same byte sequence so DbUnit's
     * stock {@code BytesDataType.typeCast(...)} can decode it through
     * its Base64 path. The marker mirrors the existing
     * {@code uuid'…'} shape used for UUID binary columns; the
     * substitution is text-level so columns whose declared type is
     * not {@code BYTES} pass straight through DbUnit's regular
     * string-cell path (a non-binary column carrying
     * {@code hex'…'} as literal content is the same edge case as a
     * VARCHAR column legitimately storing {@code [NULL]}: rare in
     * practice; the marker syntax is reserved by api contract).
     *
     * @param content the raw dataset XML text
     * @return the rewritten text with {@code hex'…'} occurrences
     *         replaced by their Base64 equivalent
     * @throws IllegalArgumentException when an {@code hex'…'} marker
     *         contains an odd-length / non-hex inner sequence; the
     *         message includes the offending marker text so the
     *         test author can correct it
     */
    private static String rewriteHexMarkersAsBase64(String content) {
        if (!content.contains("hex'")) {
            return content;
        }
        Matcher matcher = HEX_MARKER_PATTERN.matcher(content);
        StringBuilder rewritten = new StringBuilder(content.length());
        while (matcher.find()) {
            String hex = matcher.group(1);
            byte[] bytes;
            try {
                bytes = HexFormat.of().parseHex(hex);
            } catch (IllegalArgumentException invalidHex) {
                throw new IllegalArgumentException(
                        "Malformed hex'…' marker: " + matcher.group()
                                + " (inner hex must be an even-length string of 0-9a-fA-F)",
                        invalidHex);
            }
            String base64 = Base64.getEncoder().encodeToString(bytes);
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(base64));
        }
        matcher.appendTail(rewritten);
        return rewritten.toString();
    }
}
