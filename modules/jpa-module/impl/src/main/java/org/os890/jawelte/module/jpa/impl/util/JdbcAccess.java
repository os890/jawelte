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
package org.os890.jawelte.module.jpa.impl.util;

import java.sql.Connection;
import java.sql.SQLException;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.engine.jdbc.connections.spi.JdbcConnectionAccess;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.SessionFactoryImplementor;

/**
 * Borrows a JDBC {@link Connection} from Hibernate's pool
 * <em>without</em> constructing an {@code EntityManager} / Session.
 * Used by cleanup strategies and by the
 * {@code InformationSchemaTableNameResolver} — both need raw JDBC
 * for DDL / metadata work; the EntityManager and its persistence
 * context are pure overhead in those paths.
 *
 * <p>Implementation: unwraps the active EMF to a Hibernate
 * {@link SessionFactoryImplementor}, asks the
 * {@link JdbcServices} for the bootstrap-flavour
 * {@link JdbcConnectionAccess} (which goes through the same
 * {@code ConnectionProvider} the runtime sessions use, so the
 * connection IS pooled), and lends the connection out for the
 * duration of the supplied {@link ConnectionWork} block.
 *
 * <p>Compared with the previous
 * {@code emf.createEntityManager() → session.unwrap(Session.class).doWork(…) → em.close()}
 * dance, this saves the per-call EntityManager allocation,
 * persistence-context setup, and teardown — typically a few
 * milliseconds per cleanup × every test method × every persistence
 * unit. Bounded but real.
 *
 * <p>Hibernate-specific: the {@link SessionFactoryImplementor} +
 * {@link JdbcServices} unwrap is a Hibernate SPI hop. The cleanup
 * strategies that use this helper are themselves Hibernate-aware
 * already (they use {@code Session.doWork} pre-refactor), so no new
 * coupling is introduced.
 */
public class JdbcAccess {

    private JdbcAccess() {
    }

    /**
     * Function passed to {@link #run(EntityManagerFactory, ConnectionWork)} —
     * gets handed a borrowed JDBC connection and may throw
     * {@link SQLException} so callers can rely on standard
     * try/catch around JDBC calls without forcing a checked-to-runtime
     * wrap inside the lambda. Mirrors the shape of Hibernate's
     * {@code Work} interface.
     */
    @FunctionalInterface
    public interface ConnectionWork {
        /**
         * Run the work with the borrowed connection.
         *
         * @param connection the borrowed JDBC connection (do NOT close —
         *                   {@link #run} releases it back to the pool)
         * @throws SQLException any JDBC failure the body raises
         */
        void execute(Connection connection) throws SQLException;
    }

    /**
     * Borrow a pooled JDBC connection from the EMF, run {@code work}
     * with it, and release it back to the pool. The release happens
     * in a {@code finally} so a thrown exception from {@code work}
     * does not leak the connection.
     *
     * @param entityManagerFactory the EMF to source the connection from
     * @param work                 the JDBC body to run
     * @throws SQLException if {@code obtainConnection},
     *                      {@code work.execute}, or
     *                      {@code releaseConnection} throws
     */
    public static void run(EntityManagerFactory entityManagerFactory, ConnectionWork work) throws SQLException {
        SessionFactoryImplementor sessionFactory =
                entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        JdbcConnectionAccess connectionAccess = sessionFactory.getServiceRegistry()
                .requireService(JdbcServices.class)
                .getBootstrapJdbcConnectionAccess();
        Connection connection = connectionAccess.obtainConnection();
        try {
            work.execute(connection);
        } finally {
            try {
                connectionAccess.releaseConnection(connection);
            } catch (SQLException releaseFailure) {
                // Don't shadow a failure thrown by `work`; surface only when there
                // was no in-flight failure. This mirrors how try-with-resources'
                // close() failures are subordinate to the body's throw, but
                // SQLException isn't AutoCloseable-suppressible without extra
                // bookkeeping — this branch is the equivalent.
                throw releaseFailure;
            }
        }
    }
}
