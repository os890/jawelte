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
package org.os890.jawelte.tests.jta.scenario57;

import java.io.PrintWriter;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.XAConnection;
import javax.sql.XADataSource;

import org.h2.jdbcx.JdbcDataSource;

/**
 * Test-only {@link XADataSource} that delegates to H2's
 * {@link JdbcDataSource} but counts how many physical
 * {@link XAConnection}s it opens vs. how many are closed (via the
 * {@link CountingXaConnection} wrapper it hands back). The test uses
 * the open/close balance to prove the production wrapper's no-tx path
 * releases the physical connection.
 */
public class CountingXaDataSource implements XADataSource {

    private final JdbcDataSource delegate = new JdbcDataSource();

    private final AtomicInteger opened = new AtomicInteger();

    private final AtomicInteger physicalClosed = new AtomicInteger();

    /** Default constructor. */
    public CountingXaDataSource() {
    }

    /**
     * @param url the JDBC URL forwarded to the H2 delegate
     */
    public void setURL(String url) {
        delegate.setURL(url);
    }

    /**
     * @param user the JDBC user forwarded to the H2 delegate
     */
    public void setUser(String user) {
        delegate.setUser(user);
    }

    /**
     * @param password the JDBC password forwarded to the H2 delegate
     */
    public void setPassword(String password) {
        delegate.setPassword(password);
    }

    /**
     * @return how many physical {@link XAConnection}s have been opened
     */
    public int openedCount() {
        return opened.get();
    }

    /**
     * @return how many physical {@link XAConnection}s have been closed
     */
    public int physicalClosedCount() {
        return physicalClosed.get();
    }

    @Override
    public XAConnection getXAConnection() throws SQLException {
        opened.incrementAndGet();
        return new CountingXaConnection(delegate.getXAConnection(), physicalClosed);
    }

    @Override
    public XAConnection getXAConnection(String user, String password) throws SQLException {
        opened.incrementAndGet();
        return new CountingXaConnection(delegate.getXAConnection(user, password), physicalClosed);
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
