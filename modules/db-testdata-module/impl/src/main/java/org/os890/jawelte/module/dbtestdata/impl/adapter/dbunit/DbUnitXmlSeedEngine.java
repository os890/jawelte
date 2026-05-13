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
import java.util.EnumMap;
import java.util.Map;

import jakarta.annotation.Priority;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ReplacementDataSet;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.os890.jawelte.module.dbtestdata.api.SeedSpec;
import org.os890.jawelte.module.dbtestdata.api.SeedSpec.SeedMode;
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
        IDataSet dataset;
        try {
            FlatXmlDataSetBuilder builder = new FlatXmlDataSetBuilder();
            builder.setColumnSensing(true);
            IDataSet flatDataset = builder.build(new StringReader(datasetContent));
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
        } catch (Exception executionFailure) {
            throw new RuntimeException(executionFailure.getMessage(), executionFailure);
        }
    }

    private static void applyVendorDataTypeFactory(
            IDatabaseConnection dbunitConnection, Connection connection) {
        IDataTypeFactory factory = DataTypeFactoryResolver.resolveFactory(connection);
        if (factory != null) {
            dbunitConnection.getConfig()
                    .setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, factory);
        }
    }
}
