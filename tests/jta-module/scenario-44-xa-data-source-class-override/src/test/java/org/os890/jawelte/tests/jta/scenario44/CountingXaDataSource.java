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
package org.os890.jawelte.tests.jta.scenario44;

import java.io.PrintWriter;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.XAConnection;
import javax.sql.XADataSource;

import org.h2.jdbcx.JdbcDataSource;

/**
 * Test-only {@link XADataSource} that delegates everything to H2's
 * {@link JdbcDataSource} but counts its own constructions and
 * {@code getXAConnection} calls. Selected via the
 * {@code org.os890.jawelte.module.jta.xa-data-source-class} MP
 * Config key — the test asserts the counters move, proving the
 * override path is wired.
 */
public class CountingXaDataSource implements XADataSource {

    /** Bumped on every constructor call. */
    public static final AtomicInteger CONSTRUCTION_COUNT = new AtomicInteger();

    /** Bumped on every {@link #getXAConnection()} call. */
    public static final AtomicInteger XA_CONNECTION_COUNT = new AtomicInteger();

    private final JdbcDataSource delegate = new JdbcDataSource();

    /** Public no-arg constructor required by the resolver's reflection path. */
    public CountingXaDataSource() {
        CONSTRUCTION_COUNT.incrementAndGet();
    }

    /**
     * Standard {@code setURL} setter the resolver invokes.
     *
     * @param url the JDBC URL
     */
    public void setURL(String url) {
        delegate.setURL(url);
    }

    /**
     * Standard {@code setUser} setter the resolver invokes.
     *
     * @param user the JDBC user
     */
    public void setUser(String user) {
        delegate.setUser(user);
    }

    /**
     * Standard {@code setPassword} setter the resolver invokes.
     *
     * @param password the JDBC password
     */
    public void setPassword(String password) {
        delegate.setPassword(password);
    }

    @Override
    public XAConnection getXAConnection() throws SQLException {
        XA_CONNECTION_COUNT.incrementAndGet();
        return delegate.getXAConnection();
    }

    @Override
    public XAConnection getXAConnection(String user, String password) throws SQLException {
        XA_CONNECTION_COUNT.incrementAndGet();
        return delegate.getXAConnection(user, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }
}
