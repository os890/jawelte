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
package org.os890.jawelte.module.dbtestdata.impl.util;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Locale;

import org.dbunit.dataset.datatype.IDataTypeFactory;

/**
 * Resolves the correct DbUnit {@link IDataTypeFactory} for the JDBC
 * vendor sitting on the supplied {@link Connection}. Returns
 * {@code null} when the vendor is not in the recognised list, when
 * the matching {@code org.dbunit.ext.*DataTypeFactory} class is not
 * on the classpath, or when {@link DatabaseMetaData} probing fails
 * &mdash; in every miss-case the caller falls back to DbUnit's
 * default factory.
 *
 * <p>Reflection is used so the impl module does not pick up a hard
 * compile-time dependency on the optional {@code org.dbunit.ext.*}
 * packages; they ship inside the DbUnit jar but the resolver stays
 * resilient if a future modularisation moves them out.</p>
 *
 * <p>Vendor mapping (case-insensitive substring of
 * {@code DatabaseMetaData.getDatabaseProductName()}):</p>
 *
 * <ul>
 *   <li>{@code h2} &rarr;
 *       {@code org.dbunit.ext.h2.H2DataTypeFactory} &mdash; native
 *       UUID column type plus {@code UuidAwareBytesDataType} for
 *       {@code BINARY(16)} UUID columns;</li>
 *   <li>{@code postgresql} &rarr;
 *       {@code org.dbunit.ext.postgresql.PostgresqlDataTypeFactory}
 *       &mdash; native {@code uuid} and array column types;</li>
 *   <li>{@code mysql} / {@code mariadb} &rarr;
 *       {@code org.dbunit.ext.mysql.MySqlDataTypeFactory} &mdash;
 *       {@code BIT} and unsigned numerics;</li>
 *   <li>{@code oracle} &rarr;
 *       {@code org.dbunit.ext.oracle.OracleDataTypeFactory} &mdash;
 *       {@code RAW}, {@code NUMBER} precision handling.</li>
 * </ul>
 *
 * <p>The class is stateless; callers can invoke
 * {@link #resolveFactory(Connection)} from any thread.</p>
 */
public class DataTypeFactoryResolver {

    private DataTypeFactoryResolver() {
    }

    /**
     * Resolve the DbUnit data-type factory matching the JDBC vendor
     * exposed by {@code connection}'s metadata.
     *
     * @param connection an open JDBC connection
     * @return the matching factory, or {@code null} when no match is
     *         possible (unknown vendor / missing ext class / metadata
     *         probe failed)
     */
    public static IDataTypeFactory resolveFactory(Connection connection) {
        String productName = readProductName(connection);
        if (productName == null) {
            return null;
        }
        String className = factoryClassNameFor(productName);
        if (className == null) {
            return null;
        }
        return instantiateFactory(className);
    }

    private static String readProductName(Connection connection) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            String productName = metaData.getDatabaseProductName();
            return productName == null ? null : productName.toLowerCase(Locale.ROOT);
        } catch (SQLException sqlFailure) {
            return null;
        }
    }

    private static String factoryClassNameFor(String productName) {
        if (productName.contains("h2")) {
            return "org.dbunit.ext.h2.H2DataTypeFactory";
        }
        if (productName.contains("postgresql")) {
            return "org.dbunit.ext.postgresql.PostgresqlDataTypeFactory";
        }
        if (productName.contains("mysql") || productName.contains("mariadb")) {
            return "org.dbunit.ext.mysql.MySqlDataTypeFactory";
        }
        if (productName.contains("oracle")) {
            return "org.dbunit.ext.oracle.OracleDataTypeFactory";
        }
        return null;
    }

    private static IDataTypeFactory instantiateFactory(String className) {
        try {
            Class<?> factoryClass = Class.forName(className);
            return (IDataTypeFactory) factoryClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException missing) {
            return null;
        } catch (ClassCastException invalidType) {
            return null;
        }
    }
}
