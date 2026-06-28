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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.ConnectionEventListener;
import javax.sql.StatementEventListener;
import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;

/**
 * Test-only {@link XAConnection} that delegates everything to a real
 * H2 {@code XAConnection} but counts {@link #close()} calls on the
 * <em>physical</em> connection. Forwards {@code addConnectionEventListener}
 * to the delegate so that closing the logical handle obtained from
 * {@link #getConnection()} fires {@code connectionClosed} to any listener
 * the production wrapper registered — which is how the wrapper's no-tx
 * close-on-handle-close bridge is exercised.
 */
public class CountingXaConnection implements XAConnection {

    private final XAConnection delegate;

    private final AtomicInteger physicalCloseCount;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    CountingXaConnection(XAConnection delegate, AtomicInteger physicalCloseCount) {
        this.delegate = delegate;
        this.physicalCloseCount = physicalCloseCount;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return delegate.getConnection();
    }

    @Override
    public void close() throws SQLException {
        // Idempotent: count + delegate exactly once so a double close
        // (event callback + explicit close) does not skew the counter.
        if (closed.compareAndSet(false, true)) {
            physicalCloseCount.incrementAndGet();
            delegate.close();
        }
    }

    @Override
    public XAResource getXAResource() throws SQLException {
        return delegate.getXAResource();
    }

    @Override
    public void addConnectionEventListener(ConnectionEventListener listener) {
        delegate.addConnectionEventListener(listener);
    }

    @Override
    public void removeConnectionEventListener(ConnectionEventListener listener) {
        delegate.removeConnectionEventListener(listener);
    }

    @Override
    public void addStatementEventListener(StatementEventListener listener) {
        delegate.addStatementEventListener(listener);
    }

    @Override
    public void removeStatementEventListener(StatementEventListener listener) {
        delegate.removeStatementEventListener(listener);
    }
}
