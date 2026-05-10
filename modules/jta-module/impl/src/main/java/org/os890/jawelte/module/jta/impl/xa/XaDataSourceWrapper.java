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
package org.os890.jawelte.module.jta.impl.xa;

import java.io.PrintWriter;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import javax.sql.DataSource;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAResource;

import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
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
 * <p>Each {@link #getConnection()} call:
 * <ol>
 *   <li>asks the underlying {@link XADataSource} for a fresh
 *       {@link XAConnection},</li>
 *   <li>resolves the active JTA {@link Transaction} via the active
 *       {@link TransactionStrategy} and enlists the
 *       {@link XAResource} on it,</li>
 *   <li>registers a {@link Synchronization} that delists and closes
 *       the {@link XAConnection} on transaction completion,</li>
 *   <li>returns the underlying {@link Connection}.</li>
 * </ol>
 *
 * <p>If no JTA transaction is active on the calling thread the
 * wrapper returns a non-enlisted connection — the JPA provider must
 * not call this wrapper outside a JTA transaction in JTA-coordinator
 * mode, so the path exists only as a defensive fallback for diagnostic
 * tooling and connection-validation calls.
 */
public class XaDataSourceWrapper implements DataSource {

    private static final Logger LOG = System.getLogger(XaDataSourceWrapper.class.getName());

    private final XADataSource delegate;

    private final String persistenceUnitName;

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
        return enlistedConnection(delegate.getXAConnection());
    }

    @Override
    public Connection getConnection(String user, String password) throws SQLException {
        return enlistedConnection(delegate.getXAConnection(user, password));
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

    private Connection enlistedConnection(XAConnection xaConnection) throws SQLException {
        Transaction transaction = currentTransactionOrNull();
        if (transaction == null) {
            // Defensive: no JTA tx active. Return a non-enlisted
            // connection. Hibernate's JTA coordinator should never
            // ask for one outside a transaction — but connection
            // validation / startup probes might.
            return xaConnection.getConnection();
        }
        try {
            XAResource xaResource = xaConnection.getXAResource();
            transaction.enlistResource(xaResource);
            transaction.registerSynchronization(
                    new XaConnectionSynchronization(xaConnection, xaResource, transaction));
            return xaConnection.getConnection();
        } catch (RollbackException | SystemException jtaFailure) {
            closeQuietly(xaConnection);
            throw new SQLException(
                    "Failed to enlist XAResource for persistence unit '" + persistenceUnitName + "'",
                    jtaFailure);
        } catch (RuntimeException | SQLException unexpected) {
            closeQuietly(xaConnection);
            throw unexpected;
        }
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

    private static void closeQuietly(XAConnection xaConnection) {
        try {
            xaConnection.close();
        } catch (SQLException loggedAndIgnored) {
            LOG.log(Level.WARNING, "Failed to close XAConnection after enlistment failure",
                    loggedAndIgnored);
        }
    }

    /**
     * {@link Synchronization} that delists and closes the
     * {@link XAConnection} on transaction completion. Registered once
     * per enlistment so the connection's resources are released even
     * if the consumer forgets to close the {@link Connection} handle
     * (Hibernate's JTA coordinator does close it, but the
     * {@code Synchronization} is the authoritative cleanup point).
     */
    private static class XaConnectionSynchronization implements Synchronization {

        private final XAConnection xaConnection;

        private final XAResource xaResource;

        private final Transaction transaction;

        XaConnectionSynchronization(XAConnection xaConnection, XAResource xaResource, Transaction transaction) {
            this.xaConnection = xaConnection;
            this.xaResource = xaResource;
            this.transaction = transaction;
        }

        @Override
        public void beforeCompletion() {
            // No-op — Hibernate's JTA coordinator runs its own
            // beforeCompletion synchronization for the EM flush.
        }

        @Override
        public void afterCompletion(int status) {
            try {
                int delistFlag = status == Status.STATUS_COMMITTED
                        ? XAResource.TMSUCCESS
                        : XAResource.TMFAIL;
                transaction.delistResource(xaResource, delistFlag);
            } catch (SystemException loggedAndIgnored) {
                LOG.log(Level.WARNING, "Failed to delist XAResource on transaction completion",
                        loggedAndIgnored);
            } catch (IllegalStateException alreadyDelisted) {
                // The TM may have already delisted at commit/rollback time;
                // treat as expected.
            } finally {
                try {
                    xaConnection.close();
                } catch (SQLException loggedAndIgnored) {
                    LOG.log(Level.WARNING, "Failed to close XAConnection on transaction completion",
                            loggedAndIgnored);
                }
            }
        }
    }

}
