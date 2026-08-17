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
package org.os890.jawelte.tests.datasource.scenario07;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import javax.sql.DataSource;

/**
 * A vendor {@code DataSource} whose only job is to be observable: it
 * records every instance the factory builds and every instance that
 * gets closed, in a static list that outlives the CDI containers the
 * subjects boot.
 *
 * <p>{@link AutoCloseable} on purpose — that is the first of the three
 * closing shapes the lifecycle adapter handles, and the one a real
 * pooled data source uses.
 */
public class RecordingDataSource implements DataSource, AutoCloseable {

    private static final List<RecordingDataSource> CREATED = new CopyOnWriteArrayList<>();
    private static final List<RecordingDataSource> CLOSED = new CopyOnWriteArrayList<>();

    private String url;

    /** No-arg constructor required by the reflective factory. */
    public RecordingDataSource() {
        CREATED.add(this);
    }

    /**
     * Setter the reflective factory applies the {@code url} attribute
     * through.
     *
     * @param url the declared url
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * @return the url this instance was configured with
     */
    public String url() {
        return url;
    }

    /** Every instance built so far, in construction order. */
    public static List<RecordingDataSource> created() {
        return List.copyOf(CREATED);
    }

    /** Every instance closed so far, in close order. */
    public static List<RecordingDataSource> closed() {
        return List.copyOf(CLOSED);
    }

    /** Forget everything recorded so far. */
    public static void reset() {
        CREATED.clear();
        CLOSED.clear();
    }

    @Override
    public void close() {
        CLOSED.add(this);
    }

    @Override
    public Connection getConnection() throws SQLException {
        throw new SQLFeatureNotSupportedException("this data source records lifecycle, it does not connect");
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnection();
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        // nothing to log
    }

    @Override
    public void setLoginTimeout(int seconds) {
        // no connection, no timeout
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("not a wrapper for " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }
}
