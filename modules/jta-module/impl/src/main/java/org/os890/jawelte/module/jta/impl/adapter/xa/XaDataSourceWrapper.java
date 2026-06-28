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
package org.os890.jawelte.module.jta.impl.adapter.xa;

import java.io.PrintWriter;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.DataSource;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAResource;

import jakarta.transaction.RollbackException;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.port.TransactionStrategy;

/**
 * JDBC {@link DataSource} that fronts an {@link XADataSource} and
 * returns connections enlisted in the calling thread's active JTA
 * transaction. Hibernate's JTA coordinator picks this wrapper up via
 * {@code jakarta.persistence.jtaDataSource} (set by the active
 * {@code PersistencePropertyResolver}) and uses the resulting
 * {@code Connection} for all JDBC work in that PU; the connection's
 * {@link XAResource} is enlisted with the active
 * {@link Transaction} so multi-PU writes flow through the JTA
 * implementation's two-phase-commit machinery.
 *
 * <p>Connections are <strong>cached per JTA transaction</strong>.
 * The first {@link #getConnection()} call within a JTA tx asks the
 * underlying {@link XADataSource} for a fresh {@link XAConnection},
 * enlists its {@link XAResource}, registers a {@link Synchronization}
 * for cleanup, caches both the {@code XAConnection} and the
 * {@code Connection} handle keyed by the active
 * {@link Transaction}, and returns the handle. Subsequent calls
 * within the same tx return the cached {@code Connection} — Hibernate
 * may borrow + return its connection many times during one JTA tx
 * (especially under
 * {@code DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION}), and
 * caching avoids creating a new {@code XAResource} per call.
 *
 * <p>If no JTA transaction is active on the calling thread the
 * wrapper returns a non-enlisted, non-cached connection — the JPA
 * provider must not call this wrapper outside a JTA transaction in
 * JTA-coordinator mode, so the path exists only as a defensive
 * fallback for diagnostic tooling and connection-validation calls.
 * Because no {@code Synchronization} is registered on that path, the
 * wrapper instead attaches a {@link ConnectionEventListener} so the
 * underlying {@link XAConnection} is closed when the caller closes
 * (or errors on) the logical handle — closing the handle alone would
 * not release the physical {@code XAConnection} (JDBC
 * {@code PooledConnection} contract), leaking it otherwise.
 *
 * <p>Cleanup is driven by the registered {@code Synchronization}:
 * its {@code afterCompletion} removes the entry from both caches and
 * closes the {@code XAConnection}. The handle returned to the caller
 * (Hibernate) is closed by the caller; the underlying
 * {@code XAConnection} is the project's responsibility.
 */
public class XaDataSourceWrapper implements DataSource {

    private static final Logger LOG = System.getLogger(XaDataSourceWrapper.class.getName());

    private final XADataSource delegate;

    private final String persistenceUnitName;

    /**
     * Per-JTA-tx cache of the {@link Connection} handle returned to
     * Hibernate. Keyed by the active {@link Transaction} so two calls
     * to {@link #getConnection()} inside one JTA tx return the same
     * handle.
     */
    private final Map<Transaction, Connection> cachedConnections = new ConcurrentHashMap<>();

    /**
     * Per-JTA-tx cache of the underlying {@link XAConnection}. The
     * {@link Synchronization} closes this on tx completion so the
     * pool's resources are released.
     */
    private final Map<Transaction, XAConnection> cachedXaConnections = new ConcurrentHashMap<>();

    /**
     * Construct the wrapper over a configured {@link XADataSource}.
     *
     * @param delegate            the {@link XADataSource} to front;
     *                            for jpa-module tests this is an H2
     *                            {@code JdbcDataSource} configured
     *                            with the per-PU URL / user / pass
     * @param persistenceUnitName the persistence unit name the wrapper
     *                            represents — used in log messages and
     *                            error reporting only
     */
    public XaDataSourceWrapper(XADataSource delegate, String persistenceUnitName) {
        this.delegate = delegate;
        this.persistenceUnitName = persistenceUnitName;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return managedConnection(null, null);
    }

    @Override
    public Connection getConnection(String user, String password) throws SQLException {
        return managedConnection(user, password);
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
        // java.sql.CommonDataSource defines this method on the JUL Logger
        // class; XADataSource also does. Forward to the delegate.
        return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        if (iface.isInstance(delegate)) {
            return iface.cast(delegate);
        }
        throw new SQLException(
                "Cannot unwrap " + getClass().getName() + " to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this) || iface.isInstance(delegate);
    }

    /**
     * The persistence unit name this wrapper represents, for logging
     * and diagnostics.
     *
     * @return the persistence unit name
     */
    public String getPersistenceUnitName() {
        return persistenceUnitName;
    }

    private Connection managedConnection(String user, String password) throws SQLException {
        Transaction transaction = currentTransactionOrNull();
        if (transaction == null) {
            // Defensive: no JTA tx active. Return a non-enlisted,
            // non-cached connection. Hibernate's JTA coordinator
            // should never ask for one outside a tx — but connection
            // validation / startup probes might.
            //
            // No tx means no Synchronization will ever fire to release
            // the underlying XAConnection, and closing the logical
            // handle does NOT close the physical XAConnection (JDBC
            // PooledConnection contract). Bridge the two: close the
            // XAConnection when the caller closes (or errors on) the
            // handle, so this defensive path doesn't leak the physical
            // connection + its socket.
            XAConnection xaConnection = openXa(user, password);
            xaConnection.addConnectionEventListener(new ConnectionEventListener() {
                @Override
                public void connectionClosed(ConnectionEvent event) {
                    closeQuietly(xaConnection, "after no-transaction connection handle closed");
                }

                @Override
                public void connectionErrorOccurred(ConnectionEvent event) {
                    closeQuietly(xaConnection, "after no-transaction connection error");
                }
            });
            return xaConnection.getConnection();
        }
        Connection cached = cachedConnections.get(transaction);
        if (cached != null) {
            return cached;
        }
        XAConnection xaConnection = openXa(user, password);
        try {
            XAResource xaResource = xaConnection.getXAResource();
            transaction.enlistResource(xaResource);
            Connection connection = xaConnection.getConnection();
            cachedXaConnections.put(transaction, xaConnection);
            cachedConnections.put(transaction, connection);
            transaction.registerSynchronization(new TxScopedCleanupSynchronization(transaction, this));
            return connection;
        } catch (RollbackException | SystemException jtaFailure) {
            cachedConnections.remove(transaction);
            cachedXaConnections.remove(transaction);
            closeQuietly(xaConnection, "after enlistment failure");
            throw new SQLException(
                    "Failed to enlist XAResource for persistence unit '" + persistenceUnitName + "'",
                    jtaFailure);
        } catch (RuntimeException | SQLException unexpected) {
            cachedConnections.remove(transaction);
            cachedXaConnections.remove(transaction);
            closeQuietly(xaConnection, "after enlistment failure");
            throw unexpected;
        }
    }

    private XAConnection openXa(String user, String password) throws SQLException {
        return user == null ? delegate.getXAConnection() : delegate.getXAConnection(user, password);
    }

    private static Transaction currentTransactionOrNull() {
        try {
            TransactionManager transactionManager =
                    TestContext.loadService(TransactionStrategy.class).getTransactionManager();
            return transactionManager == null ? null : transactionManager.getTransaction();
        } catch (SystemException sysFailure) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void closeQuietly(XAConnection xaConnection, String context) {
        try {
            xaConnection.close();
        } catch (SQLException loggedAndIgnored) {
            LOG.log(Level.WARNING, "Failed to close XAConnection " + context, loggedAndIgnored);
        }
    }

    /**
     * {@link Synchronization} that drops the cached connection +
     * {@link XAConnection} entries and closes the underlying
     * {@code XAConnection} on transaction completion. Registered once
     * per JTA tx (on first {@link #getConnection()} that enlists an
     * XAResource); fires whether the tx commits or rolls back.
     */
    private static class TxScopedCleanupSynchronization implements Synchronization {

        private final Transaction transaction;

        private final XaDataSourceWrapper owner;

        TxScopedCleanupSynchronization(Transaction transaction, XaDataSourceWrapper owner) {
            this.transaction = transaction;
            this.owner = owner;
        }

        @Override
        public void beforeCompletion() {
            // No-op — Hibernate's JTA coordinator runs its own
            // beforeCompletion synchronization for the EM flush.
        }

        @Override
        public void afterCompletion(int status) {
            // Status is informational only. The XAResource has already
            // been delisted + committed/rolled-back by the TM during
            // its own commit/rollback flow; afterCompletion's job is
            // to release pooled resources.
            owner.cachedConnections.remove(transaction);
            XAConnection xaConnection = owner.cachedXaConnections.remove(transaction);
            int unusedStatus = status;
            if (xaConnection == null) {
                if (unusedStatus == jakarta.transaction.Status.STATUS_UNKNOWN) {
                    LOG.log(Level.WARNING, "JTA tx completed with STATUS_UNKNOWN — no cached XAConnection to release");
                }
                return;
            }
            try {
                xaConnection.close();
            } catch (SQLException loggedAndIgnored) {
                LOG.log(Level.WARNING, "Failed to close XAConnection on transaction completion",
                        loggedAndIgnored);
            }
        }
    }

}
