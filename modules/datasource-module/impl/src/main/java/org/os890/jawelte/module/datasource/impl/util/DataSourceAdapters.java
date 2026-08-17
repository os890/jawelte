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
package org.os890.jawelte.module.datasource.impl.util;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.XADataSource;

/**
 * Adapts the two non-{@link DataSource} types
 * {@code @DataSourceDefinition.className()} is allowed to name —
 * {@link XADataSource} and {@link ConnectionPoolDataSource} — to
 * {@code DataSource}.
 *
 * <p>Both are connection <em>factories</em> rather than data sources:
 * they hand out an {@code XAConnection} / {@code PooledConnection}
 * wrapper whose {@code getConnection()} yields the usable JDBC
 * connection. The adapters do exactly that unwrapping and nothing
 * else, so that a test declaring an XA driver class and a test
 * declaring a plain one look identical from the injection point.
 *
 * <p>Deliberately <em>not</em> a pool and <em>not</em> transaction
 * aware. Enlisting an {@code XADataSource} with a transaction manager
 * is what {@code @DataSourceDefinition(transactional = true)} asks
 * for, and that is a jta-module concern (jta-module already ships an
 * {@code XaDataSourceWrapper} for it) rather than something to
 * approximate here.
 */
public abstract class DataSourceAdapters {

    /** Suppress instantiation; the class is a static-method holder. */
    protected DataSourceAdapters() {
    }

    /**
     * Adapt an {@link XADataSource} to {@link DataSource}.
     *
     * @param xaDataSource the configured XA data source
     * @return a data source view over it
     */
    public static DataSource of(XADataSource xaDataSource) {
        return new AbstractDelegatingDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return xaDataSource.getXAConnection().getConnection();
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return xaDataSource.getXAConnection(username, password).getConnection();
            }

            @Override
            public PrintWriter getLogWriter() throws SQLException {
                return xaDataSource.getLogWriter();
            }

            @Override
            public void setLogWriter(PrintWriter out) throws SQLException {
                xaDataSource.setLogWriter(out);
            }

            @Override
            public void setLoginTimeout(int seconds) throws SQLException {
                xaDataSource.setLoginTimeout(seconds);
            }

            @Override
            public int getLoginTimeout() throws SQLException {
                return xaDataSource.getLoginTimeout();
            }

            @Override
            public Object delegate() {
                return xaDataSource;
            }
        };
    }

    /**
     * Adapt a {@link ConnectionPoolDataSource} to {@link DataSource}.
     *
     * @param pooledDataSource the configured connection-pool data source
     * @return a data source view over it
     */
    public static DataSource of(ConnectionPoolDataSource pooledDataSource) {
        return new AbstractDelegatingDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return pooledDataSource.getPooledConnection().getConnection();
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return pooledDataSource.getPooledConnection(username, password).getConnection();
            }

            @Override
            public PrintWriter getLogWriter() throws SQLException {
                return pooledDataSource.getLogWriter();
            }

            @Override
            public void setLogWriter(PrintWriter out) throws SQLException {
                pooledDataSource.setLogWriter(out);
            }

            @Override
            public void setLoginTimeout(int seconds) throws SQLException {
                pooledDataSource.setLoginTimeout(seconds);
            }

            @Override
            public int getLoginTimeout() throws SQLException {
                return pooledDataSource.getLoginTimeout();
            }

            @Override
            public Object delegate() {
                return pooledDataSource;
            }
        };
    }

    /**
     * The shared parts of both adapters: {@code unwrap} / {@code isWrapperFor}
     * reaching the adapted object, and the parent-logger call every
     * {@code DataSource} has to answer.
     */
    private abstract static class AbstractDelegatingDataSource implements DataSource {

        abstract Object delegate();

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            if (iface.isInstance(delegate())) {
                return iface.cast(delegate());
            }
            throw new SQLException(getClass().getName() + " is not a wrapper for " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this) || iface.isInstance(delegate());
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException(
                    "The adapted " + delegate().getClass().getName() + " does not expose a parent logger");
        }
    }
}
